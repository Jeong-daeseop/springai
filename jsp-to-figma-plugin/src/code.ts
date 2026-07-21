import { unzipSync, strFromU8 } from "fflate";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex } from "@noble/hashes/utils";

type Bounds = { x: number; y: number; width: number; height: number };
type NodeData = { id: string; parentId?: string; type: string; tag?: string; label?: string; text?: string; visible: boolean; bounds?: Bounds; styles?: Record<string,string>; children?: string[] };
type AssetData={id:string;path:string;mimeType:string;byteLength:number;contentHash:string};
type CandidateData={type:string;nodeIds:string[];confidence:number;evidence:string[]};
type DocumentData = { schemaVersion: string; captureId: string; documentKey: string; contentHash: string; page: {title?: string;documentWidth?:number;documentHeight?:number}; nodes: NodeData[]; assets?:AssetData[]; tokens?:Record<string,string>; componentCandidates?:CandidateData[]; warnings?: {code:string}[] };
type Manifest = { packageVersion:string; mimeType:string; captureId:string; documentKey:string; contentHash:string; entries:{path:string;byteLength:number;sha256:string}[] };
type BuildOptions={candidateTypes:string[];createStyles:boolean};

figma.showUI(__html__, { width: 380, height: 480 });

async function digest(bytes: Uint8Array): Promise<string> {
  return bytesToHex(sha256(bytes));
}

async function parse(bytes: Uint8Array): Promise<{manifest:Manifest;document:DocumentData;files:Record<string,Uint8Array>}> {
  if (bytes.length > 50 * 1024 * 1024) throw new Error("패키지가 50MB 제한을 초과했습니다.");
  const files = unzipSync(bytes);
  if (!files["manifest.json"] || !files["document.json"]) throw new Error("필수 entry가 없습니다.");
  const manifest = JSON.parse(strFromU8(files["manifest.json"])) as Manifest;
  const document = JSON.parse(strFromU8(files["document.json"])) as DocumentData;
  if (manifest.packageVersion !== "figpack-v1" || manifest.mimeType !== "application/vnd.springai.figpack+zip") throw new Error("지원하지 않는 package입니다.");
  if (document.schemaVersion !== "rendered-design-document-v1") throw new Error("지원하지 않는 document schema입니다.");
  if (manifest.captureId !== document.captureId || manifest.documentKey !== document.documentKey || manifest.contentHash !== document.contentHash) throw new Error("manifest/document 식별자가 일치하지 않습니다.");
  if ((document as unknown as {source?:{contentHash?:string}}).source?.contentHash) throw new Error("contentHash는 document 최상위에만 허용됩니다.");
  if (manifest.entries.length > 5100) throw new Error("entry 수 제한을 초과했습니다.");
  let total = 0;
  const declared=new Set<string>();
  for (const entry of manifest.entries) {
    if (entry.path.startsWith("/") || entry.path.includes("..") || entry.path.includes("\\")) throw new Error("안전하지 않은 entry 경로입니다.");
    if(declared.has(entry.path)||[...declared].some(value=>value.toLowerCase()===entry.path.toLowerCase()))throw new Error("중복 또는 대소문자 충돌 entry입니다."); declared.add(entry.path);
    const value = files[entry.path]; if (!value) throw new Error(`누락 entry: ${entry.path}`);
    total += value.length; if (total > 100 * 1024 * 1024) throw new Error("압축 해제 크기 제한을 초과했습니다.");
    if (value.length !== entry.byteLength || await digest(value) !== entry.sha256) throw new Error(`entry hash 불일치: ${entry.path}`);
  }
  const assetIds=new Set<string>();
  for(const asset of document.assets??[]){
    if(assetIds.has(asset.id))throw new Error("asset ID가 중복됩니다.");assetIds.add(asset.id);
    const value=files[asset.path];if(!value)throw new Error(`asset entry가 없습니다: ${asset.path}`);
    if(value.length!==asset.byteLength||await digest(value)!==asset.contentHash)throw new Error(`asset 메타데이터가 entry와 일치하지 않습니다: ${asset.path}`);
  }
  const actual=Object.keys(files).filter(name=>name!=="manifest.json"&&!name.endsWith("/")); if(actual.length!==declared.size||actual.some(name=>!declared.has(name)))throw new Error("manifest에 선언되지 않은 entry가 있습니다.");
  const nodeIds=new Set(document.nodes.map(node=>node.id)); if(nodeIds.size!==document.nodes.length)throw new Error("node ID가 중복됩니다.");
  const index=new Map(document.nodes.map((node,i)=>[node.id,i]));
  for(const node of document.nodes){if(node.parentId&&(!nodeIds.has(node.parentId)||(index.get(node.parentId)??Infinity)>=(index.get(node.id)??-1)))throw new Error("부모 node가 자식보다 먼저 선언되어야 합니다.");if(node.children?.some(id=>!nodeIds.has(id)))throw new Error("child node 참조가 없습니다.");}
  return {manifest,document,files};
}

const color = (css?: string): RGB | null => {
  const match = css?.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/); return match ? {r:+match[1]/255,g:+match[2]/255,b:+match[3]/255}:null;
};
const box=(css?:string):[number,number,number,number]=>{const values=(css??"").split(/\s+/).map(value=>parseFloat(value)).filter(Number.isFinite);if(values.length===1)return[values[0],values[0],values[0],values[0]];if(values.length===2)return[values[0],values[1],values[0],values[1]];if(values.length===3)return[values[0],values[1],values[2],values[1]];if(values.length>=4)return[values[0],values[1],values[2],values[3]];return[0,0,0,0];};

async function createText(node: NodeData): Promise<TextNode> {
  const text = figma.createText();
  const family=(node.styles?.fontFamily?.split(",")[0]??"Inter").replace(/["']/g,"").trim(); const weight=Number.parseInt(node.styles?.fontWeight??"400"); const style=weight>=600?"Bold":"Regular";
  try { await figma.loadFontAsync({ family, style }); text.fontName={family,style}; }
  catch { await figma.loadFontAsync({family:"Roboto",style:"Regular"}); text.fontName={family:"Roboto",style:"Regular"}; }
  text.characters = (node.text ?? node.label ?? "").slice(0, 10000);
  const fill = color(node.styles?.color); if(fill) text.fills=[{type:"SOLID",color:fill}];
  const size=parseFloat(node.styles?.fontSize??"");if(Number.isFinite(size)&&size>0&&size<=512)text.fontSize=size;
  const lineHeight=parseFloat(node.styles?.lineHeight??"");if(Number.isFinite(lineHeight)&&lineHeight>0)text.lineHeight={unit:"PIXELS",value:lineHeight};
  const letterSpacing=parseFloat(node.styles?.letterSpacing??"");if(Number.isFinite(letterSpacing))text.letterSpacing={unit:"PIXELS",value:letterSpacing};
  const alignment=node.styles?.textAlign?.toUpperCase();if(["LEFT","CENTER","RIGHT","JUSTIFIED"].includes(alignment??""))text.textAlignHorizontal=alignment as "LEFT"|"CENTER"|"RIGHT"|"JUSTIFIED";
  const opacity=parseFloat(node.styles?.opacity??"1");if(Number.isFinite(opacity))text.opacity=Math.max(0,Math.min(1,opacity));
  return text;
}

function createAssetNode(node:NodeData,document:DocumentData,files:Record<string,Uint8Array>):SceneNode|undefined{
  const asset=document.assets?.find(value=>value.id===node.id);if(!asset)return undefined;const bytes=files[asset.path];if(!bytes)throw new Error(`asset entry가 없습니다: ${asset.path}`);
  if(node.styles?.backgroundAsset==="true")return undefined;
  if(asset.mimeType==="image/svg+xml"){const svg=strFromU8(bytes);if(/<script|\son\w+=|(?:href|xlink:href)=["'](?:https?:|\/\/)/i.test(svg))throw new Error("안전하지 않은 SVG입니다.");return figma.createNodeFromSvg(svg);}
  const rectangle=figma.createRectangle();const copy=new Uint8Array(bytes.length);copy.set(bytes);const image=figma.createImage(copy);rectangle.fills=[{type:"IMAGE",imageHash:image.hash,scaleMode:"FILL"}];return rectangle;
}

async function ensureLocalStyles(document:DocumentData):Promise<{paint?:PaintStyle;text?:TextStyle}>{
  const result:{paint?:PaintStyle;text?:TextStyle}={};
  const background=color(document.tokens?.["color.background"]);if(background){const name="Website/Background";result.paint=(await figma.getLocalPaintStylesAsync()).find(style=>style.name===name);if(!result.paint){result.paint=figma.createPaintStyle();result.paint.name=name;result.paint.paints=[{type:"SOLID",color:background}];}}
  const family=(document.tokens?.["font.family"]?.split(",")[0]??"Roboto").replace(/["']/g,"").trim();let font:FontName={family,style:"Regular"};try{await figma.loadFontAsync(font);}catch{font={family:"Roboto",style:"Regular"};await figma.loadFontAsync(font);}
  const textName="Website/Body";result.text=(await figma.getLocalTextStylesAsync()).find(style=>style.name===textName);if(!result.text){result.text=figma.createTextStyle();result.text.name=textName;result.text.fontName=font;const size=parseFloat(document.tokens?.["font.size"]??"");if(Number.isFinite(size)&&size>0)result.text.fontSize=size;}
  return result;
}

async function applyBackgroundAsset(frame:FrameNode,node:NodeData,document:DocumentData,files:Record<string,Uint8Array>){
  if(node.styles?.backgroundAsset!=="true")return;const asset=document.assets?.find(value=>value.id===node.id);if(!asset)return;const bytes=files[asset.path];if(!bytes||asset.mimeType==="image/svg+xml")return;const copy=new Uint8Array(bytes.length);copy.set(bytes);const image=figma.createImage(copy);frame.fills=[{type:"IMAGE",imageHash:image.hash,scaleMode:"FILL"}];
}

async function build(document: DocumentData,files:Record<string,Uint8Array>,options:BuildOptions): Promise<FrameNode> {
  const duplicate = figma.currentPage.findOne(node => node.type === "FRAME" && node.getPluginData("documentKey") === document.documentKey && node.getPluginData("contentHash") === document.contentHash);
  if (duplicate) throw new Error("같은 documentKey/contentHash Frame이 이미 존재합니다.");
  const root = figma.createFrame(); root.name = `IMPORTING ${document.page.title || document.captureId}`; root.clipsContent=false;
  if(document.page.documentWidth&&document.page.documentHeight)root.resize(Math.max(1,document.page.documentWidth),Math.max(1,document.page.documentHeight));
  root.setPluginData("temporary", "true");
  const created = new Map<string, SceneNode>();
  const localStyles=options.createStyles?await ensureLocalStyles(document):{};
  try {
    for (const item of document.nodes) {
      let node: SceneNode;
      const assetNode=createAssetNode(item,document,files);
      if(assetNode)node=assetNode;
      else if (["BUTTON","HEADING","LABEL","TH","TEXT","PSEUDO"].includes(item.type)) {node = await createText(item);if(localStyles.text)await node.setTextStyleIdAsync(localStyles.text.id);}
      else { const frame=figma.createFrame(); frame.clipsContent=["hidden","clip"].includes(item.styles?.overflow??""); const background=color(item.styles?.backgroundColor); frame.fills=background?[{type:"SOLID",color:background}]:[];
        const confidence=parseFloat(item.styles?.layoutConfidence??"0");if(item.styles?.layoutMode?.startsWith("AUTO_LAYOUT")&&confidence>=0.9) { frame.layoutMode=item.styles.layoutMode==="AUTO_LAYOUT_HORIZONTAL"?"HORIZONTAL":"VERTICAL"; const gap=parseFloat(item.styles.gap ?? "0"); if(Number.isFinite(gap))frame.itemSpacing=Math.max(0,gap);const [top,right,bottom,left]=box(item.styles.padding);frame.paddingTop=Math.max(0,top);frame.paddingRight=Math.max(0,right);frame.paddingBottom=Math.max(0,bottom);frame.paddingLeft=Math.max(0,left);const align=item.styles.alignItems;frame.counterAxisAlignItems=align==="center"?"CENTER":align==="flex-end"?"MAX":"MIN";const justify=item.styles.justifyContent;frame.primaryAxisAlignItems=justify==="center"?"CENTER":justify==="flex-end"?"MAX":justify==="space-between"?"SPACE_BETWEEN":"MIN"; }
        const radius=parseFloat(item.styles?.borderRadius??"");if(Number.isFinite(radius))frame.cornerRadius=Math.max(0,Math.min(1000,radius));
        const border=item.styles?.border?.match(/(\d+(?:\.\d+)?)px\s+\w+\s+(rgba?\(.+\))/);const stroke=border?color(border[2]):null;if(border&&stroke){frame.strokes=[{type:"SOLID",color:stroke}];frame.strokeWeight=Number(border[1]);}
        const opacity=parseFloat(item.styles?.opacity??"1");if(Number.isFinite(opacity))frame.opacity=Math.max(0,Math.min(1,opacity));
        await applyBackgroundAsset(frame,item,document,files);if(localStyles.paint&&item.styles?.backgroundAsset!=="true"&&item.styles?.backgroundColor===document.tokens?.["color.background"])await frame.setFillStyleIdAsync(localStyles.paint.id);
        node=frame; }
      node.name = `${item.type}:${item.id}`; node.visible=item.visible;
      const bounds=item.bounds; if(bounds){ node.x=bounds.x; node.y=bounds.y; if("resize" in node)node.resize(Math.max(1,bounds.width),Math.max(1,bounds.height)); }
      const parent=item.parentId ? created.get(item.parentId) : root;
      if (parent && "appendChild" in parent) (parent as ChildrenMixin).appendChild(node); else root.appendChild(node);
      if(parent?.type==="FRAME"&&parent.layoutMode!=="NONE"&&item.styles?.position==="absolute"&&"layoutPositioning" in node)node.layoutPositioning="ABSOLUTE";
      if (bounds && parent && parent !== root && "x" in parent) { const parentItem=document.nodes.find(value=>value.id===item.parentId); if(parentItem?.bounds){node.x=bounds.x-parentItem.bounds.x;node.y=bounds.y-parentItem.bounds.y;} }
      created.set(item.id,node);
    }
    const selected=new Set(options.candidateTypes);const candidateNodes=(document.componentCandidates??[]).filter(candidate=>selected.has(candidate.type)&&candidate.confidence>=0.8).map(candidate=>({candidate,nodeId:candidate.nodeIds[0]})).filter(value=>!!value.nodeId).sort((left,right)=>(document.nodes.findIndex(node=>node.id===right.nodeId)-document.nodes.findIndex(node=>node.id===left.nodeId)));
    for(const {candidate,nodeId} of candidateNodes){const target=created.get(nodeId);if(!target||target.type==="COMPONENT"||target.type==="INSTANCE")continue;const component=figma.createComponentFromNode(target);component.name=`${candidate.type}/${nodeId}`;component.setPluginData("componentCandidateType",candidate.type);created.set(nodeId,component);}
    root.name=document.page.title || `Website ${document.captureId}`; root.setPluginData("schemaVersion",document.schemaVersion); root.setPluginData("captureId",document.captureId); root.setPluginData("documentKey",document.documentKey); root.setPluginData("contentHash",document.contentHash); root.setPluginData("temporary","");
    figma.currentPage.selection=[root]; figma.viewport.scrollAndZoomIntoView([root]); return root;
  } catch(error) { root.remove(); throw error; }
}

let pending: {manifest:Manifest;document:DocumentData;files:Record<string,Uint8Array>} | undefined;
figma.ui.onmessage = async (message: {type:string;bytes?:ArrayBuffer;candidateTypes?:string[];createStyles?:boolean}) => {
  try {
    if(message.type==="CANCEL"){pending=undefined;return;}
    if(message.type==="PREVIEW" && message.bytes) { pending=await parse(new Uint8Array(message.bytes)); figma.ui.postMessage({type:"PREVIEW_READY",candidates:[...new Set((pending.document.componentCandidates??[]).filter(value=>value.confidence>=0.8).map(value=>value.type))],message:`검증 완료\n화면: ${pending.document.page.title || "제목 없음"}\n노드: ${pending.document.nodes.length}개\n경고: ${pending.document.warnings?.length ?? 0}개\nComponent와 Style 옵션을 확인한 뒤 생성하세요.`}); return; }
    if(message.type==="CREATE") { if(!pending)throw new Error("먼저 figpack을 선택해 검증하세요."); const frame=await build(pending.document,pending.files,{candidateTypes:message.candidateTypes??[],createStyles:message.createStyles===true}); figma.ui.postMessage({type:"RESULT",message:`생성 완료: ${frame.name}\n노드 ${pending.document.nodes.length}개\n자산 ${pending.document.assets?.length ?? 0}개\n선택 Component 유형 ${(message.candidateTypes??[]).length}개\n경고 ${pending.document.warnings?.length ?? 0}개`}); }
  } catch(error) { pending=undefined; figma.ui.postMessage({type:"ERROR",message:`처리 실패: ${error instanceof Error ? error.message : "알 수 없는 오류"}`}); }
};
