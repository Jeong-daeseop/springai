import { unzipSync, strFromU8 } from "fflate";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex } from "@noble/hashes/utils";

type Bounds = { x: number; y: number; width: number; height: number };
type NodeData = { id: string; parentId?: string; type: string; tag?: string; label?: string; text?: string; value?: string; visible: boolean; bounds?: Bounds; styles?: Record<string,string>; children?: string[] };
type AssetData={id:string;path:string;mimeType:string;byteLength:number;contentHash:string};
type CandidateData={type:string;nodeIds:string[];confidence:number;evidence:string[]};
type DocumentData = { schemaVersion: string; captureId: string; documentKey: string; contentHash: string; page: {title?: string;documentWidth?:number;documentHeight?:number}; nodes: NodeData[]; assets?:AssetData[]; tokens?:Record<string,string>; componentCandidates?:CandidateData[]; warnings?: {code:string}[] };
type Manifest = { packageVersion:string; mimeType:string; captureId:string; documentKey:string; contentHash:string; entries:{path:string;byteLength:number;sha256:string}[] };
type BuildOptions={candidateTypes:string[];createStyles:boolean;createVariables:boolean;keepPartialOnFailure:boolean};
type Parsed = {manifest:Manifest;document:DocumentData;files:Record<string,Uint8Array>};

/** R8 Part B(04번 문서 §11): springai의 RenderedDesignBundle과 동일한 형태(zip 안 bundle.json). */
type ComponentMatchData = {selectorHint:string; nodeIdsByViewport:Record<string,string>; status:"MATCHED_ALL"|"HIDDEN_IN_SOME"|"MOVED"};
type BundleData = {schemaVersion:string; bundleId:string; viewportArtifacts:Record<string,string>; componentMatches:ComponentMatchData[]; breakpointObservations:unknown[]; warnings:{code:string;nodeId:string|null;message:string}[]};
const BUNDLE_VIEWPORT_ORDER = ["desktop","tablet","mobile"] as const;

figma.showUI(__html__, { width: 380, height: 640 });

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

/** zip 최상위에 bundle.json이 있으면 R8 Part B 다중 viewport 번들, 없으면 기존 단일 figpack이다. */
function isBundleZip(bytes: Uint8Array): boolean {
  if (bytes.length > 150 * 1024 * 1024) throw new Error("bundle 패키지가 150MB 제한을 초과했습니다.");
  const files = unzipSync(bytes);
  return !!files["bundle.json"];
}

async function parseBundle(bytes: Uint8Array): Promise<{bundle:BundleData; viewports:Map<string,Parsed>}> {
  const files = unzipSync(bytes);
  const bundleEntry = files["bundle.json"];
  if (!bundleEntry) throw new Error("bundle.json entry가 없습니다.");
  const bundle = JSON.parse(strFromU8(bundleEntry)) as BundleData;
  if (bundle.schemaVersion !== "rendered-design-bundle-v1") throw new Error("지원하지 않는 bundle schema입니다.");
  const viewports = new Map<string, Parsed>();
  for (const viewport of BUNDLE_VIEWPORT_ORDER) {
    const figpackBytes = files[`viewports/${viewport}.figpack`];
    if (!figpackBytes) continue;
    viewports.set(viewport, await parse(figpackBytes));
  }
  if (viewports.size === 0) throw new Error("bundle에 유효한 viewport figpack이 없습니다.");
  return {bundle, viewports};
}

const color = (css?: string): RGB | null => {
  const match = css?.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/); return match ? {r:+match[1]/255,g:+match[2]/255,b:+match[3]/255}:null;
};
const colorWithAlpha = (css?: string): RGBA | null => {
  const match = css?.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
  return match ? {r:+match[1]/255,g:+match[2]/255,b:+match[3]/255,a:match[4]!==undefined?+match[4]:1} : null;
};
/** 알파가 0에 가까운(=CSS상 투명한) 색은 fill을 만들지 않는다. alpha가 있으면 SolidPaint.opacity로 반영한다. */
const solidPaint = (css?: string): SolidPaint | null => {
  const rgba = colorWithAlpha(css);
  if (!rgba || rgba.a <= 0.01) return null;
  return { type: "SOLID", color: { r: rgba.r, g: rgba.g, b: rgba.b }, opacity: rgba.a };
};
const shadowEffect = (css?: string): DropShadowEffect | null => {
  if (!css || css === "none") return null;
  const rgba = colorWithAlpha(css);
  const numbers = css.replace(/rgba?\([^)]*\)/, "").match(/-?\d+(?:\.\d+)?/g)?.map(Number) ?? [];
  const [offsetX = 0, offsetY = 0, radius = 0] = numbers;
  return { type: "DROP_SHADOW", color: rgba ?? { r: 0, g: 0, b: 0, a: 0.25 }, offset: { x: offsetX, y: offsetY }, radius: Math.max(0, radius), visible: true, blendMode: "NORMAL" };
};
const box=(css?:string):[number,number,number,number]=>{const values=(css??"").split(/\s+/).map(value=>parseFloat(value)).filter(Number.isFinite);if(values.length===1)return[values[0],values[0],values[0],values[0]];if(values.length===2)return[values[0],values[1],values[0],values[1]];if(values.length===3)return[values[0],values[1],values[2],values[1]];if(values.length>=4)return[values[0],values[1],values[2],values[3]];return[0,0,0,0];};

async function createText(node: NodeData): Promise<TextNode> {
  const text = figma.createText();
  const family=(node.styles?.fontFamily?.split(",")[0]??"Inter").replace(/["']/g,"").trim(); const weight=Number.parseInt(node.styles?.fontWeight??"400"); const style=weight>=600?"Bold":"Regular";
  try { await figma.loadFontAsync({ family, style }); text.fontName={family,style}; }
  catch { await figma.loadFontAsync({family:"Roboto",style:"Regular"}); text.fontName={family:"Roboto",style:"Regular"}; }
  text.characters = (node.text ?? node.label ?? "").slice(0, 10000);
  const fillPaint = solidPaint(node.styles?.color); if(fillPaint) text.fills=[fillPaint];
  const size=parseFloat(node.styles?.fontSize??"");if(Number.isFinite(size)&&size>0&&size<=512)text.fontSize=size;
  if(size===0)text.visible=false;
  const lineHeight=parseFloat(node.styles?.lineHeight??"");if(Number.isFinite(lineHeight)&&lineHeight>0){text.lineHeight={unit:"PIXELS",value:lineHeight};text.textAlignVertical="CENTER";}
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

async function ensureLocalStyles(document:DocumentData):Promise<{paint?:PaintStyle;text?:TextStyle;effect?:EffectStyle}>{
  const result:{paint?:PaintStyle;text?:TextStyle;effect?:EffectStyle}={};
  const backgroundPaint=solidPaint(document.tokens?.["color.background"]);if(backgroundPaint){const name="Website/Background";result.paint=(await figma.getLocalPaintStylesAsync()).find(style=>style.name===name);if(!result.paint){result.paint=figma.createPaintStyle();result.paint.name=name;result.paint.paints=[backgroundPaint];}}
  const family=(document.tokens?.["font.family"]?.split(",")[0]??"Roboto").replace(/["']/g,"").trim();let font:FontName={family,style:"Regular"};try{await figma.loadFontAsync(font);}catch{font={family:"Roboto",style:"Regular"};await figma.loadFontAsync(font);}
  const textName="Website/Body";result.text=(await figma.getLocalTextStylesAsync()).find(style=>style.name===textName);
  if(!result.text){result.text=figma.createTextStyle();result.text.name=textName;result.text.fontName=font;const size=parseFloat(document.tokens?.["font.size"]??"");if(Number.isFinite(size)&&size>0)result.text.fontSize=size;}
  /** 이전 import에서 이미 만들어진 스타일을 재사용하는 경우, 그 스타일이 실제로 물고 있는 폰트(이번 실행에서 계산한 font와 다를 수 있음)를
   * 별도로 로드해야 한다 — 안 그러면 setTextStyleIdAsync()가 "unloaded font"로 실패한다. 로드 자체가 안 되는 폰트면 공유 스타일 재사용을 포기한다. */
  else{try{await figma.loadFontAsync(result.text.fontName as FontName);}catch{result.text=undefined;}}
  const shadow=shadowEffect(document.tokens?.["shadow"]);if(shadow){const name="Website/Shadow";result.effect=(await figma.getLocalEffectStylesAsync()).find(style=>style.name===name);if(!result.effect){result.effect=figma.createEffectStyle();result.effect.name=name;result.effect.effects=[shadow];}}
  return result;
}

/** Release 1 선택 기능: 사용자가 명시적으로 opt-in한 경우에만 호출하며 자동 publish하지 않는다(03번 §10.6). */
async function ensureLocalVariables(document:DocumentData):Promise<void>{
  const tokens:[string,"COLOR"|"FLOAT",unknown][]=[
    ["color/text","COLOR",color(document.tokens?.["color.text"])],
    ["color/background","COLOR",color(document.tokens?.["color.background"])],
    ["number/fontSize","FLOAT",(()=>{const value=parseFloat(document.tokens?.["font.size"]??"");return Number.isFinite(value)?value:null;})()],
    ["number/radius","FLOAT",(()=>{const value=parseFloat(document.tokens?.["radius"]??"");return Number.isFinite(value)?value:null;})()],
  ];
  if(!tokens.some(([,,value])=>value!==null))return;
  const collectionName="Website Tokens";
  const collection=(await figma.variables.getLocalVariableCollectionsAsync()).find(item=>item.name===collectionName)??figma.variables.createVariableCollection(collectionName);
  const modeId=collection.modes[0].modeId;
  const existing=await figma.variables.getLocalVariablesAsync();
  for(const [name,type,value] of tokens){
    if(value===null)continue;
    const variable=existing.find(item=>item.name===name&&item.variableCollectionId===collection.id)??figma.variables.createVariable(name,collection,type);
    variable.setValueForMode(modeId,value as VariableValue);
  }
}

async function applyBackgroundAsset(frame:FrameNode,node:NodeData,document:DocumentData,files:Record<string,Uint8Array>){
  if(node.styles?.backgroundAsset!=="true")return;const asset=document.assets?.find(value=>value.id===node.id);if(!asset)return;const bytes=files[asset.path];if(!bytes||asset.mimeType==="image/svg+xml")return;const copy=new Uint8Array(bytes.length);copy.set(bytes);const image=figma.createImage(copy);frame.fills=[{type:"IMAGE",imageHash:image.hash,scaleMode:"FILL"}];
}

/** container(일반 요소)와 BUTTON Frame이 공유하는 배경/테두리/radius/opacity/Auto Layout/Style 적용 로직. */
async function styleFrame(frame:FrameNode,item:NodeData,document:DocumentData,files:Record<string,Uint8Array>,localStyles:{paint?:PaintStyle;text?:TextStyle;effect?:EffectStyle}):Promise<void>{
  frame.clipsContent=["hidden","clip"].includes(item.styles?.overflow??"");
  const backgroundPaint=solidPaint(item.styles?.backgroundColor);frame.fills=backgroundPaint?[backgroundPaint]:[];
  const confidence=parseFloat(item.styles?.layoutConfidence??"0");
  if(item.styles?.layoutMode?.startsWith("AUTO_LAYOUT")&&confidence>=0.9) { frame.layoutMode=item.styles.layoutMode==="AUTO_LAYOUT_HORIZONTAL"?"HORIZONTAL":"VERTICAL"; const gap=parseFloat(item.styles.gap ?? "0"); if(Number.isFinite(gap))frame.itemSpacing=Math.max(0,gap);const [top,right,bottom,left]=box(item.styles.padding);frame.paddingTop=Math.max(0,top);frame.paddingRight=Math.max(0,right);frame.paddingBottom=Math.max(0,bottom);frame.paddingLeft=Math.max(0,left);const align=item.styles.alignItems;frame.counterAxisAlignItems=align==="center"?"CENTER":align==="flex-end"?"MAX":"MIN";const justify=item.styles.justifyContent;frame.primaryAxisAlignItems=justify==="center"?"CENTER":justify==="flex-end"?"MAX":justify==="space-between"?"SPACE_BETWEEN":"MIN"; }
  const radius=parseFloat(item.styles?.borderRadius??"");if(Number.isFinite(radius))frame.cornerRadius=Math.max(0,Math.min(1000,radius));
  const borderSide=(css?:string)=>{const match=css?.match(/(\d+(?:\.\d+)?)px\s+\w+\s+(rgba?\(.+\))/);return match?{weight:Number(match[1]),paint:solidPaint(match[2])}:null;};
  const sides=[borderSide(item.styles?.borderTop),borderSide(item.styles?.borderRight),borderSide(item.styles?.borderBottom),borderSide(item.styles?.borderLeft)];
  if(sides.some(side=>side?.paint)){
    const sharedPaint=sides.find(side=>side?.paint)!.paint as SolidPaint; frame.strokes=[sharedPaint];
    frame.strokeTopWeight=sides[0]?.paint?sides[0].weight:0; frame.strokeRightWeight=sides[1]?.paint?sides[1].weight:0;
    frame.strokeBottomWeight=sides[2]?.paint?sides[2].weight:0; frame.strokeLeftWeight=sides[3]?.paint?sides[3].weight:0;
  } else {
    const border=item.styles?.border?.match(/(\d+(?:\.\d+)?)px\s+\w+\s+(rgba?\(.+\))/);const strokePaint=border?solidPaint(border[2]):null;if(border&&strokePaint){frame.strokes=[strokePaint];frame.strokeWeight=Number(border[1]);}
  }
  const opacity=parseFloat(item.styles?.opacity??"1");if(Number.isFinite(opacity))frame.opacity=Math.max(0,Math.min(1,opacity));
  await applyBackgroundAsset(frame,item,document,files);if(localStyles.paint&&item.styles?.backgroundAsset!=="true"&&item.styles?.backgroundColor===document.tokens?.["color.background"])await frame.setFillStyleIdAsync(localStyles.paint.id);
  if(localStyles.effect&&item.styles?.boxShadow&&item.styles.boxShadow===document.tokens?.["shadow"])await frame.setEffectStyleIdAsync(localStyles.effect.id);
}

/** TextNode는 IndividualStrokesMixin이 없어 border-bottom 전용 같은 한쪽 면 테두리를 못 그린다. 얇은 Rectangle을 형제로 추가해 대체한다. */
function addBorderDividers(item:NodeData,relX:number,relY:number,bounds:Bounds,parent:ChildrenMixin):void{
  const side=(css?:string)=>{const match=css?.match(/(\d+(?:\.\d+)?)px\s+\w+\s+(rgba?\(.+\))/);return match?{weight:Number(match[1]),paint:solidPaint(match[2])}:null;};
  const draw=(x:number,y:number,w:number,h:number,paint:SolidPaint)=>{const rect=figma.createRectangle();rect.x=relX+x;rect.y=relY+y;rect.resize(Math.max(1,w),Math.max(1,h));rect.fills=[paint];parent.appendChild(rect);};
  const top=side(item.styles?.borderTop);if(top?.paint&&top.weight>0)draw(0,0,bounds.width,top.weight,top.paint);
  const bottom=side(item.styles?.borderBottom);if(bottom?.paint&&bottom.weight>0)draw(0,bounds.height-bottom.weight,bounds.width,bottom.weight,bottom.paint);
  const left=side(item.styles?.borderLeft);if(left?.paint&&left.weight>0)draw(0,0,left.weight,bounds.height,left.paint);
  const right=side(item.styles?.borderRight);if(right?.paint&&right.weight>0)draw(bounds.width-right.weight,0,right.weight,bounds.height,right.paint);
}

async function build(document: DocumentData,files:Record<string,Uint8Array>,options:BuildOptions): Promise<{frame:FrameNode;boundaryWarnings:string[];created:Map<string,SceneNode>}> {
  const duplicate = figma.currentPage.findOne(node => node.type === "FRAME" && node.getPluginData("documentKey") === document.documentKey && node.getPluginData("contentHash") === document.contentHash);
  if (duplicate) throw new Error("같은 documentKey/contentHash Frame이 이미 존재합니다.");
  const root = figma.createFrame(); root.name = `IMPORTING ${document.page.title || document.captureId}`; root.clipsContent=false;
  if(document.page.documentWidth&&document.page.documentHeight)root.resize(Math.max(1,document.page.documentWidth),Math.max(1,document.page.documentHeight));
  root.setPluginData("temporary", "true");
  const created = new Map<string, SceneNode>();
  const localStyles=options.createStyles?await ensureLocalStyles(document):{};
  const boundaryWarnings:string[]=[];
  try {
    for (const item of document.nodes) {
      let node: SceneNode;
      const assetNode=createAssetNode(item,document,files);
      if(assetNode)node=assetNode;
      else if (item.type==="BUTTON") {
        const frame=figma.createFrame(); await styleFrame(frame,item,document,files,localStyles);
        const text=await createText(item); if(localStyles.text)await text.setTextStyleIdAsync(localStyles.text.id);
        text.textAlignHorizontal="CENTER"; frame.appendChild(text);
        if(item.bounds){ text.x=Math.max(0,(item.bounds.width-text.width)/2); text.y=Math.max(0,(item.bounds.height-text.height)/2); }
        node=frame;
      }
      else if (["HEADING","LABEL","TH","TEXT","PSEUDO"].includes(item.type) && !item.children?.length) {node = await createText(item);if(localStyles.text)await node.setTextStyleIdAsync(localStyles.text.id);}
      else if (item.tag==="select" && item.value) {
        const frame=figma.createFrame(); await styleFrame(frame,item,document,files,localStyles);
        const text=await createText({...item,text:item.value}); if(localStyles.text)await text.setTextStyleIdAsync(localStyles.text.id);
        frame.appendChild(text);
        if(item.bounds){ const [,,,left]=box(item.styles?.padding); text.x=Math.max(0,left); text.y=Math.max(0,(item.bounds.height-text.height)/2); }
        /** 네이티브 select 화살표는 브라우저가 OS 수준에서 그려 DOM/CSS로 캡처가 원리적으로 불가능하다. 관례적으로 항상 합성해 넣는다. */
        const arrow=await createText({...item,text:"▾",styles:{...item.styles,fontSize:"18px",fontWeight:"700",lineHeight:"normal"}});
        frame.appendChild(arrow);
        if(item.bounds){ arrow.x=Math.max(0,item.bounds.width-25-arrow.width); arrow.y=Math.max(0,(item.bounds.height-arrow.height)/2); }
        node=frame;
      }
      else { const frame=figma.createFrame(); await styleFrame(frame,item,document,files,localStyles); node=frame; }
      node.name = `${item.type}:${item.id}`; node.visible=item.visible && node.visible;
      const bounds=item.bounds; if(bounds){ node.x=bounds.x; node.y=bounds.y; if(node.type!=="TEXT" && "resize" in node)node.resize(Math.max(1,bounds.width),Math.max(1,bounds.height)); }
      const parent=item.parentId ? created.get(item.parentId) : root;
      if (parent && "appendChild" in parent) (parent as ChildrenMixin).appendChild(node); else root.appendChild(node);
      if(parent?.type==="FRAME"&&parent.layoutMode!=="NONE"&&item.styles?.position==="absolute"&&"layoutPositioning" in node)node.layoutPositioning="ABSOLUTE";
      if (bounds && parent && parent !== root && "x" in parent) { const parentItem=document.nodes.find(value=>value.id===item.parentId); if(parentItem?.bounds){node.x=bounds.x-parentItem.bounds.x;node.y=bounds.y-parentItem.bounds.y;
        if(node.x<0||node.y<0||node.x+bounds.width>parentItem.bounds.width||node.y+bounds.height>parentItem.bounds.height)boundaryWarnings.push(`${item.id}가 부모 ${item.parentId} 영역을 벗어납니다.`);
      } }
      /** TextNode(HEADING/LABEL/TH 등 자식 없는 leaf)는 Figma가 4면 개별 stroke를 지원하지 않아 styleFrame()의 border 처리를 받지 못한다.
       * border-bottom만 있는 구분선처럼 한쪽 면만 있는 경우를 위해 얇은 사각형을 형제로 별도 생성한다. */
      if(bounds && node.type==="TEXT" && parent && "appendChild" in parent) addBorderDividers(item,node.x,node.y,bounds,parent as ChildrenMixin);
      /** HEADING/LABEL/TH처럼 bounds가 padding 포함 전체 요소 박스인 경우 padding-top/left만큼 옮겨 CSS content-box 시작 위치와 맞춘다.
       * padding이 없는(=TEXT 분할 노드처럼 tight glyph bounds인) 경우엔 line-height로 커진 TextNode 박스 중심을 원래 glyph 중심에 맞춘다.
       * text-align이 center/right인 경우 padding-left만큼 미는 것은 부정확하므로(정렬 기준이 content-box 중앙/우측) 적용하지 않는다 — 지금은 start/left(기본값) 텍스트만 대상. */
      if(bounds && node.type==="TEXT" && "height" in node){
        const [paddingTop,,,paddingLeft]=box(item.styles?.padding);
        if(paddingTop>0) node.y+=paddingTop;
        else if(node.height>bounds.height) node.y-=(node.height-bounds.height)/2;
        const textAlign=item.styles?.textAlign;
        if(paddingLeft>0 && textAlign!=="center" && textAlign!=="right") node.x+=paddingLeft;
      }
      created.set(item.id,node);
    }
    const selected=new Set(options.candidateTypes);const candidateNodes=(document.componentCandidates??[]).filter(candidate=>selected.has(candidate.type)&&candidate.confidence>=0.8).map(candidate=>({candidate,nodeId:candidate.nodeIds[0]})).filter(value=>!!value.nodeId).sort((left,right)=>(document.nodes.findIndex(node=>node.id===right.nodeId)-document.nodes.findIndex(node=>node.id===left.nodeId)));
    for(const {candidate,nodeId} of candidateNodes){const target=created.get(nodeId);if(!target||target.type==="COMPONENT"||target.type==="INSTANCE")continue;const component=figma.createComponentFromNode(target);component.name=`${candidate.type}/${nodeId}`;component.setPluginData("componentCandidateType",candidate.type);created.set(nodeId,component);}
    if(options.createVariables)await ensureLocalVariables(document);
    root.name=document.page.title || `Website ${document.captureId}`; root.setPluginData("schemaVersion",document.schemaVersion); root.setPluginData("captureId",document.captureId); root.setPluginData("documentKey",document.documentKey); root.setPluginData("contentHash",document.contentHash); root.setPluginData("temporary","");
    figma.currentPage.selection=[root]; figma.viewport.scrollAndZoomIntoView([root]); return {frame:root,boundaryWarnings,created};
  } catch(error) {
    if(options.keepPartialOnFailure){root.name=`PARTIAL (FAILED) ${document.page.title || document.captureId}`;root.setPluginData("temporary","");figma.currentPage.selection=[root];figma.viewport.scrollAndZoomIntoView([root]);}
    else root.remove();
    throw error;
  }
}

/**
 * R8 Part B(04번 문서 §11): Desktop/Tablet/Mobile Frame을 나란히 만들고, 사용자가 선택한
 * MATCHED_ALL selectorHint만 figma.combineAsVariants()로 ComponentSet Variant 후보를
 * 만든다(체크박스로 검토 후 결합 — 사용자 게이트 결정). 개별 viewport Frame 생성 실패는
 * 경고로 남기고 계속 진행하며(부분 성공 처리), 전부 실패한 경우에만 예외를 던진다.
 * 이 1차 구현은 Style/Variable/componentCandidates 옵션은 지원하지 않는다(범위 축소).
 */
async function buildBundle(
  bundle: BundleData, viewports: Map<string, Parsed>, selectedSelectorHints: string[],
): Promise<{frames:FrameNode[];boundaryWarnings:string[];frameFailures:string[];variantsCreated:number}> {
  const GUTTER = 120;
  const frames: FrameNode[] = [];
  const createdByViewport = new Map<string, Map<string, SceneNode>>();
  const boundaryWarnings: string[] = [];
  const frameFailures: string[] = [];
  let cursorX = 0;
  for (const viewport of BUNDLE_VIEWPORT_ORDER) {
    const parsed = viewports.get(viewport);
    if (!parsed) continue;
    try {
      const {frame, boundaryWarnings: warnings, created} = await build(
        parsed.document, parsed.files, {candidateTypes:[], createStyles:false, createVariables:false, keepPartialOnFailure:false});
      frame.x = cursorX; frame.y = 0; frame.name = `[${viewport}] ${frame.name}`;
      cursorX += frame.width + GUTTER;
      frames.push(frame); createdByViewport.set(viewport, created); boundaryWarnings.push(...warnings);
    } catch (error) {
      frameFailures.push(`${viewport}: ${error instanceof Error ? error.message : "알 수 없는 오류"}`);
    }
  }
  if (frames.length === 0) throw new Error(`모든 viewport Frame 생성에 실패했습니다.\n${frameFailures.join("\n")}`);

  let variantsCreated = 0;
  const selected = new Set(selectedSelectorHints);
  const maxHeight = Math.max(...frames.map(frame => frame.height));
  for (const match of bundle.componentMatches) {
    if (match.status !== "MATCHED_ALL" || !selected.has(match.selectorHint)) continue;
    const components: ComponentNode[] = [];
    let ok = true;
    for (const viewport of BUNDLE_VIEWPORT_ORDER) {
      const nodeId = match.nodeIdsByViewport[viewport];
      if (!nodeId) { ok = false; break; }
      const createdMap = createdByViewport.get(viewport);
      const target = createdMap?.get(nodeId);
      if (!target || !createdMap) { ok = false; break; }
      const component = target.type === "COMPONENT" ? target as ComponentNode : figma.createComponentFromNode(target);
      component.name = `Viewport=${viewport}`;
      createdMap.set(nodeId, component);
      components.push(component);
    }
    if (!ok || components.length < 2) continue;
    const variantSet = figma.combineAsVariants(components, figma.currentPage);
    variantSet.name = match.selectorHint;
    variantSet.x = 0; variantSet.y = maxHeight + 200 + variantsCreated * (variantSet.height + 80);
    variantsCreated++;
  }
  figma.currentPage.selection = frames; figma.viewport.scrollAndZoomIntoView(frames);
  return {frames, boundaryWarnings, frameFailures, variantsCreated};
}

let pending: Parsed | undefined;
let pendingBundle: {bundle:BundleData; viewports:Map<string,Parsed>} | undefined;
figma.ui.onmessage = async (message: {type:string;bytes?:ArrayBuffer;candidateTypes?:string[];createStyles?:boolean;createVariables?:boolean;keepPartialOnFailure?:boolean;selectedSelectorHints?:string[]}) => {
  try {
    if(message.type==="CANCEL"){pending=undefined;pendingBundle=undefined;return;}
    if(message.type==="PREVIEW" && message.bytes) {
      const bytes=new Uint8Array(message.bytes);
      if(isBundleZip(bytes)) {
        pending=undefined;
        const bundleState=await parseBundle(bytes); pendingBundle=bundleState;
        const viewportNames=[...bundleState.viewports.keys()];
        const variantCandidates=bundleState.bundle.componentMatches.filter(match=>match.status==="MATCHED_ALL"&&BUNDLE_VIEWPORT_ORDER.every(viewport=>!bundleState.viewports.has(viewport)||!!match.nodeIdsByViewport[viewport])).map(match=>match.selectorHint);
        figma.ui.postMessage({type:"PREVIEW_READY_BUNDLE",viewports:viewportNames,variantCandidates,message:`번들 검증 완료\nviewport: ${viewportNames.join(", ")} (${viewportNames.length}/3)\nVariant 결합 후보: ${variantCandidates.length}개\n경고: ${bundleState.bundle.warnings.length}개\n결합할 컴포넌트를 선택한 뒤 생성하세요.`});
        return;
      }
      pendingBundle=undefined; pending=await parse(bytes);
      figma.ui.postMessage({type:"PREVIEW_READY",candidates:[...new Set((pending.document.componentCandidates??[]).filter(value=>value.confidence>=0.8).map(value=>value.type))],message:`검증 완료\n화면: ${pending.document.page.title || "제목 없음"}\n노드: ${pending.document.nodes.length}개\n경고: ${pending.document.warnings?.length ?? 0}개\nComponent와 Style 옵션을 확인한 뒤 생성하세요.`});
      return;
    }
    if(message.type==="CREATE") { if(!pending)throw new Error("먼저 figpack을 선택해 검증하세요."); const {frame,boundaryWarnings}=await build(pending.document,pending.files,{candidateTypes:message.candidateTypes??[],createStyles:message.createStyles===true,createVariables:message.createVariables===true,keepPartialOnFailure:message.keepPartialOnFailure===true}); figma.ui.postMessage({type:"RESULT",message:`생성 완료: ${frame.name}\n노드 ${pending.document.nodes.length}개\n자산 ${pending.document.assets?.length ?? 0}개\n선택 Component 유형 ${(message.candidateTypes??[]).length}개\n경고 ${pending.document.warnings?.length ?? 0}개\n영역 이탈 경고 ${boundaryWarnings.length}개${boundaryWarnings.length?`\n${boundaryWarnings.slice(0,5).join("\n")}`:""}`}); }
    if(message.type==="CREATE_BUNDLE") {
      if(!pendingBundle)throw new Error("먼저 bundle zip을 선택해 검증하세요.");
      const {frames,boundaryWarnings,frameFailures,variantsCreated}=await buildBundle(pendingBundle.bundle,pendingBundle.viewports,message.selectedSelectorHints??[]);
      figma.ui.postMessage({type:"RESULT",message:`생성 완료: Frame ${frames.length}개(${frames.map(frame=>frame.name).join(", ")})\nVariant 결합 ${variantsCreated}개\n영역 이탈 경고 ${boundaryWarnings.length}개${boundaryWarnings.length?`\n${boundaryWarnings.slice(0,5).join("\n")}`:""}${frameFailures.length?`\nviewport 실패 ${frameFailures.length}개\n${frameFailures.join("\n")}`:""}`});
    }
  } catch(error) { pending=undefined; pendingBundle=undefined; figma.ui.postMessage({type:"ERROR",message:`처리 실패: ${error instanceof Error ? error.message : "알 수 없는 오류"}`}); }
};
