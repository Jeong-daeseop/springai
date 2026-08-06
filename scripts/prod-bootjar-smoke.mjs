import { spawn } from 'node:child_process';
import { mkdtemp, readdir } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const projectRoot = resolve(import.meta.dirname, '..');
const libsDir = join(projectRoot, 'build', 'libs');
const jarName = (await readdir(libsDir)).find(name => name.endsWith('.jar'));
if (!jarName) throw new Error('bootJar를 찾을 수 없습니다. 먼저 bootJar task를 실행하세요.');

const port = await new Promise((resolvePort, reject) => {
  const server = createServer();
  server.once('error', reject);
  server.listen(0, '127.0.0.1', () => {
    const selected = server.address().port;
    server.close(error => error ? reject(error) : resolvePort(selected));
  });
});
const isolatedCwd = await mkdtemp(join(tmpdir(), 'springai-prod-smoke-'));
const child = spawn('java', ['-jar', join(libsDir, jarName),
  '--spring.profiles.active=prod',
  `--server.port=${port}`,
  '--spring.main.lazy-initialization=true',
  '--spring.flyway.enabled=false',
  '--spring.ai.vectorstore.redis.initialize-schema=false',
  '--management.health.defaults.enabled=false',
  '--app.db.legacy-repository-ddl-enabled=false'
], {
  cwd: isolatedCwd,
  env: {
    ...process.env,
    OPENAI_API_KEY: 'smoke-openai-key',
    APP_API_KEY: 'smoke-api-key',
    DB_URL: 'jdbc:mysql://127.0.0.1:9/smoke',
    DB_USERNAME: 'smoke-user',
    DB_PASSWORD: 'smoke-password',
    REDIS_URI: 'redis://127.0.0.1:9'
  },
  stdio: ['ignore', 'pipe', 'pipe']
});

let output = '';
child.stdout.on('data', chunk => { output += chunk; });
child.stderr.on('data', chunk => { output += chunk; });
let exited = false;
let exitCode = null;
child.once('exit', code => { exited = true; exitCode = code; });

const deadline = Date.now() + 60_000;
try {
  let home;
  while (Date.now() < deadline) {
    if (exited) throw new Error(`bootJar가 준비 전에 종료되었습니다(code=${exitCode}).\n${output}`);
    try {
      home = await fetch(`http://127.0.0.1:${port}/`);
      if (home.ok) break;
    } catch {}
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  if (!home?.ok) throw new Error(`60초 안에 UI가 준비되지 않았습니다.\n${output}`);
  const html = await home.text();
  if (!/<html[\s>]/i.test(html)) throw new Error('classpath Thymeleaf 응답이 HTML이 아닙니다.');

  const staticResponse = await fetch(`http://127.0.0.1:${port}/js/marked.min.js`);
  if (!staticResponse.ok || (await staticResponse.text()).length < 100) {
    throw new Error(`classpath 정적 리소스 검증 실패(status=${staticResponse.status})`);
  }
  const liveness = await fetch(`http://127.0.0.1:${port}/actuator/health/liveness`);
  const readiness = await fetch(`http://127.0.0.1:${port}/actuator/health/readiness`);
  if (!liveness.ok || !readiness.ok) {
    throw new Error(`health probe 분리 검증 실패(liveness=${liveness.status}, readiness=${readiness.status})`);
  }
  process.stdout.write(`WP9 prod bootJar smoke 통과: cwd=${isolatedCwd}, port=${port}\n`);
} finally {
  if (!exited) child.kill('SIGTERM');
}
