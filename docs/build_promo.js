const pptxgen = require("pptxgenjs");

// ================= 微软 Fluent 风格调色板 =================
const BG = "FFFFFF";        // 内容页背景
const DARK = "1B1A19";      // 封面/结尾深色
const PRIMARY = "0078D4";   // Microsoft 蓝
const BLUE_LT = "4CC2FF";   // 深底上的蓝
const ACCENT = "107C10";    // Xbox/Minecraft 绿（仅 Demo CTA）
const TEXT = "201F1E";      // 正文深灰
const MUTED = "605E5C";     // 次要灰
const TINT = "F3F2F1";      // Fluent 中性底
const TINT_BLUE = "EFF6FC"; // 淡蓝底
const HAIR = "E1DFDD";      // 发丝线
const G = {
  COMMON: "605E5C", RARE: "0078D4", RAINBOW: "B4009E", BLACK: "D13438",
};

const F = "Microsoft YaHei";
const W = 13.33, H = 7.5, M = 0.55;

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";
pres.author = "TakaraMiyuki";
pres.title = "猎人游戏 Manhunt 模组宣传";

// ================= 通用元件 =================
function contentHeader(slide, kicker, title) {
  slide.addText(kicker, { x: M, y: 0.38, w: 9, h: 0.3, fontSize: 11, bold: true, color: PRIMARY, fontFace: F, charSpacing: 2, margin: 0 });
  slide.addText(title, { x: M, y: 0.66, w: 11.5, h: 0.62, fontSize: 26, bold: true, color: TEXT, fontFace: F, margin: 0 });
}
function chip(slide, x, y, size, color, glyph, fontSize) {
  slide.addShape(pres.shapes.RECTANGLE, { x, y, w: size, h: size, fill: { color } });
  slide.addText(glyph, { x, y: y - 0.01, w: size, h: size, fontSize: fontSize || 15, bold: true, color: "FFFFFF", fontFace: F, align: "center", valign: "middle", margin: 0 });
}
function row(slide, x, y, w, color, glyph, head, desc, headSize, descSize) {
  chip(slide, x, y + 0.04, 0.4, color, glyph);
  slide.addText(head, { x: x + 0.58, y: y - 0.04, w: w - 0.58, h: 0.32, fontSize: headSize || 14.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  slide.addText(desc, { x: x + 0.58, y: y + 0.27, w: w - 0.58, h: 0.42, fontSize: descSize || 12, color: MUTED, fontFace: F, margin: 0 });
}
function hairline(slide, x, y, w) {
  slide.addShape(pres.shapes.LINE, { x, y, w, h: 0, line: { color: HAIR, width: 1 } });
}

// ================= S1 封面（深色） =================
{
  const s = pres.addSlide();
  s.background = { color: DARK };
  s.addText("MINECRAFT JAVA 26.2 · NEOFORGE 模组", { x: M, y: 1.35, w: 9, h: 0.34, fontSize: 13, bold: true, color: BLUE_LT, fontFace: F, charSpacing: 3, margin: 0 });
  s.addText([
    { text: "猎人游戏 ", options: { color: "FFFFFF" } },
    { text: "MANHUNT", options: { color: BLUE_LT } },
  ], { x: M, y: 1.75, w: 12.2, h: 1.15, fontSize: 54, bold: true, fontFace: F, margin: 0 });
  s.addText("追杀、逃亡与检查点追逐——一条通往末影龙的路", { x: M, y: 3.0, w: 11.5, h: 0.5, fontSize: 19, color: "D0CECE", fontFace: F, margin: 0 });

  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: 3.95, w: 3.9, h: 0.62, fill: { color: ACCENT }, rectRadius: 0.06 });
  s.addText("9 月 26 日 · Demo 开放试玩", { x: M, y: 3.95, w: 3.9, h: 0.62, fontSize: 17, bold: true, color: "FFFFFF", fontFace: F, align: "center", valign: "middle", margin: 0 });

  // 两侧阵营标签
  const camp = (x, color, name, desc) => {
    s.addShape(pres.shapes.RECTANGLE, { x, y: 5.15, w: 0.16, h: 1.1, fill: { color } });
    s.addText(name, { x: x + 0.3, y: 5.12, w: 3.4, h: 0.42, fontSize: 20, bold: true, color: "FFFFFF", fontFace: F, margin: 0 });
    s.addText(desc, { x: x + 0.3, y: 5.55, w: 4.6, h: 0.68, fontSize: 12.5, color: "C8C6C4", fontFace: F, margin: 0 });
  };
  camp(M, "107C10", "逃生者", "激活检查点 · 奔跑积累里程 · 击杀末影龙获胜");
  camp(M + 5.3, "0078D4", "猎人", "全员追杀 · 士气换取超级抽奖 · 淘汰所有逃生者获胜");

  s.addText("双模组内容：Manhunt 主玩法 + SkillCards 技能卡联动", { x: M, y: 6.85, w: 10, h: 0.32, fontSize: 11.5, color: "979491", fontFace: F, margin: 0 });
}

// ================= S2 宣言 + 关键数字 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "OVERVIEW 概览", "一人逃，全员追");
  s.addText("跑得越远，抽奖越豪华；猎人造成的每一份伤害，也在为全队积蓄士气。\n这不是简单的追逐——双方都有属于自己的成长线。",
    { x: M, y: 1.62, w: 12.2, h: 1.0, fontSize: 17, color: TEXT, fontFace: F, margin: 0, lineSpacingMultiple: 1.25 });

  const stats = [
    ["14", "张技能卡", "四档稀有度 · 技能栏轮换"],
    ["4", "档里程奖池", "食物消耗品为主 · 装备稀有"],
    ["60", "秒逃跑窗口", "开局定身猎人 · 公平起跑"],
    ["3000", "里程解锁要塞", "固定彩卡 + 全队超级抽奖"],
  ];
  const cw = 2.95, gap = 0.13, y0 = 3.1;
  stats.forEach(([num, label, sub], i) => {
    const x = M + i * (cw + gap);
    s.addShape(pres.shapes.RECTANGLE, { x, y: y0, w: cw, h: 2.35, fill: { color: TINT } });
    s.addText(num, { x: x + 0.22, y: y0 + 0.28, w: cw - 0.44, h: 0.95, fontSize: 44, bold: true, color: PRIMARY, fontFace: "Segoe UI", margin: 0 });
    s.addText(label, { x: x + 0.22, y: y0 + 1.28, w: cw - 0.44, h: 0.36, fontSize: 15, bold: true, color: TEXT, fontFace: F, margin: 0 });
    s.addText(sub, { x: x + 0.22, y: y0 + 1.66, w: cw - 0.44, h: 0.6, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
  });

  hairline(s, M, 6.0, 12.23);
  s.addText("两大阵营 · 动态人数平衡 · 与 SkillCards 技能卡模组深度联动 · 全部机制可在单人模式调试",
    { x: M, y: 6.18, w: 12.2, h: 0.35, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S3 两大阵营 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "TEAMS 阵营", "两个阵营，两条成长线");

  const col = (x, headColor, tint, name, goal, rows) => {
    s.addShape(pres.shapes.RECTANGLE, { x, y: 1.62, w: 6.0, h: 0.56, fill: { color: headColor } });
    s.addText(name, { x: x + 0.25, y: 1.62, w: 5.5, h: 0.56, fontSize: 18, bold: true, color: "FFFFFF", fontFace: F, valign: "middle", margin: 0 });
    s.addShape(pres.shapes.RECTANGLE, { x, y: 2.18, w: 6.0, h: 3.9, fill: { color: tint } });
    s.addText(goal, { x: x + 0.25, y: 2.36, w: 5.5, h: 0.4, fontSize: 13.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
    let y = 2.95;
    rows.forEach(([h, d]) => {
      s.addText(h, { x: x + 0.25, y, w: 5.5, h: 0.3, fontSize: 13, bold: true, color: TEXT, fontFace: F, margin: 0 });
      s.addText(d, { x: x + 0.25, y: y + 0.3, w: 5.5, h: 0.55, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
      y += 0.88;
    });
  };
  col(M, "107C10", "F1F6F1", "逃生者", "目标：激活检查点，击杀末影龙", [
    ["60–100 血 · 抗性 · 急迫", "血量与强化随人数比动态调整，进入末地再度强化"],
    ["里程抽奖 · 技能卡", "跑图积累里程换取资源，检查点抽取技能卡"],
    ["检查点罗盘 + 进度 bossbar", "方向与距离实时指引，激活前不暴露下一个坐标"],
  ]);
  col(M + 6.23, "0078D4", "EFF6FC", "猎人", "目标：淘汰所有逃生者", [
    ["40–60 血 · 速度 · 急迫", "人数占优时火力更强，劣势时逃生者获得强化"],
    ["士气抽奖", "全队共享士气量表，伤害达标全员超级抽奖"],
    ["追踪罗盘", "右键循环锁定逃生者实时位置"],
  ]);

  hairline(s, M, 6.35, 12.23);
  s.addText("胜负：末影龙死亡 → 逃生者胜；逃生者全部淘汰 → 猎人胜。支持 /manhunt admin start solo 单人无猎人调试。",
    { x: M, y: 6.52, w: 12.2, h: 0.35, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S4 游戏流程 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "GAMEPLAY 玩法流程", "从出生点到末影龙的六步");
  const steps = [
    ["开局集合", "全员传送出生点，逃生者获得 60 秒逃跑窗口，猎人被定身"],
    ["检查点链", "每隔 400–600 格随机刷新，激活后才解锁下一个坐标"],
    ["里程抽奖", "每移动 1 格积 1 里程，每 200 格触发一次五连抽奖"],
    ["要塞指引", "任一逃生者里程达 3000，下一检查点固定为末地要塞"],
    ["末地强化", "抗性 2 / 速度 / 跳跃提升 2 / 饱和 / 急迫 2，里程停算"],
    ["击杀末影龙", "要塞激活后全队获得末影之眼 ×12，开启传送门，屠龙即胜"],
  ];
  const cw = 3.95, ch = 2.1, gx = 0.19, gy = 0.3, x0 = M, y0 = 1.75;
  steps.forEach(([h, d], i) => {
    const x = x0 + (i % 3) * (cw + gx), y = y0 + Math.floor(i / 3) * (ch + gy);
    s.addShape(pres.shapes.RECTANGLE, { x, y, w: cw, h: ch, fill: { color: i === 5 ? TINT_BLUE : TINT } });
    s.addText(String(i + 1), { x: x + 0.22, y: y + 0.2, w: 0.62, h: 0.62, fontSize: 30, bold: true, color: PRIMARY, fontFace: "Segoe UI", margin: 0 });
    s.addText(h, { x: x + 0.22, y: y + 0.88, w: cw - 0.44, h: 0.36, fontSize: 15.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
    s.addText(d, { x: x + 0.22, y: y + 1.26, w: cw - 0.44, h: 0.72, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
  });
  s.addText("每局节奏约 30–60 分钟：跑图积累 → 检查点博弈 → 末地决战。",
    { x: M, y: 6.5, w: 12.2, h: 0.35, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S5 里程与资源抽奖 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "MILEAGE 里程系统", "跑图即抽奖：越远的路，越好的装备");

  row(s, M, 1.75, 6.1, PRIMARY, "1", "每移动 1 格 +1 里程", "水平位移累计，传送与坠落不计；进入末地后停止累计");
  row(s, M, 2.62, 6.1, PRIMARY, "200", "每 200 里程一次资源抽奖", "5 种物品的老虎机动画，不锁视野与操作，边跑边抽");
  row(s, M, 3.49, 6.1, PRIMARY, "XP", "经验条变身里程量表", "进度 = 距下次抽奖，等级 = 已触发次数");
  row(s, M, 4.36, 6.1, PRIMARY, "?", "自选领取：滚轮选择 · 中键标记", "右键领取标记物品，未标记的放弃——把选择权留给玩家");

  // 右侧四档奖池分层（颜色由浅到深 = 质量递增）
  const tiers = [
    ["1", "< 1000 里程", "生存补给：食物 · 木石 · 煤铁粒", "D6E4F0", TEXT],
    ["2", "< 2000 里程", "冒险补给：铁金铜 · 弓箭 · 红石", "A9CCE3", TEXT],
    ["3", "< 3000 里程", "精良补给：钻石 · 附魔书概率提升", "4296CF", "FFFFFF"],
    ["4", "3000+ 里程", "顶级补给：满级附魔书 · 鞘翅 · 末影之眼", "0078D4", "FFFFFF"],
  ];
  const bx = 7.1, bw = 5.68, ry0 = 1.72, rh = 0.92, rgap = 0.12;
  tiers.forEach(([n, range, items, color, txt], i) => {
    const y = ry0 + i * (rh + rgap);
    s.addShape(pres.shapes.RECTANGLE, { x: bx, y, w: bw, h: rh, fill: { color } });
    s.addText(n, { x: bx + 0.18, y: y + 0.14, w: 0.55, h: 0.64, fontSize: 26, bold: true, color: txt, fontFace: "Segoe UI", margin: 0 });
    s.addText(range, { x: bx + 0.82, y: y + 0.12, w: 2.3, h: 0.34, fontSize: 13.5, bold: true, color: txt, fontFace: F, margin: 0 });
    s.addText(items, { x: bx + 0.82, y: y + 0.47, w: bw - 1.0, h: 0.32, fontSize: 11.5, color: txt, fontFace: F, margin: 0 });
  });
  s.addText("里程越高，奖池越豪华", { x: bx, y: 6.0, w: bw, h: 0.32, fontSize: 12, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText("2000 里程后附魔书概率提升，3000 里程后出现满级高级附魔书——配合合成栏附魔，附魔书+装备直接合成附魔。",
    { x: M, y: 6.1, w: 12.2, h: 0.6, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S6 检查点与要塞 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "CHECKPOINTS 检查点", "一条只属于逃生者的寻宝路线");

  row(s, M, 1.78, 6.35, PRIMARY, "链", "检查点无限链", "间隔 400–600 格随机方向刷新，激活后才解锁下一个坐标——猎人也只能赌方向");
  row(s, M, 2.72, 6.35, PRIMARY, "标", "金块平台 + 荧石柱标记", "3×3 金块基座配三格荧石柱，远处可见；靠近 6 格自动激活");
  row(s, M, 3.66, 6.35, PRIMARY, "引", "罗盘 + bossbar 双指引", "检查点罗盘实时指向当前目标，bossbar 显示进度与直线距离");
  row(s, M, 4.6, 6.35, PRIMARY, "卡", "激活即结算", "激活者抽一张技能卡，全体逃生者获得一次不限奖池的超级抽奖");

  s.addShape(pres.shapes.RECTANGLE, { x: 7.35, y: 1.78, w: 5.43, h: 3.65, fill: { color: TINT_BLUE } });
  s.addText("3000 里程 · 决战开启", { x: 7.62, y: 2.02, w: 4.9, h: 0.4, fontSize: 17, bold: true, color: PRIMARY, fontFace: F, margin: 0 });
  s.addText([
    { text: "下一个检查点固定刷新在末地要塞正上方", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "要塞检查点必定抽到彩卡", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "全队获得末影之眼 ×12，开启传送门", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "要塞入口在地下，激活后进入杀龙阶段", options: { bullet: { code: "25B8", indent: 10 } } },
  ], { x: 7.62, y: 2.55, w: 4.95, h: 2.6, fontSize: 12.5, color: TEXT, fontFace: F, paraSpaceAfter: 10, margin: 0, valign: "top" });

  hairline(s, M, 5.95, 12.23);
  s.addText("末地强化（按人数档位）：抗性 2 · 速度 · 跳跃提升 2 · 饱和 · 急迫 2 —— 里程与抽奖在末地停算，专注决战。",
    { x: M, y: 6.15, w: 12.2, h: 0.4, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S7 猎人系统 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "HUNTERS 猎人系统", "士气：猎人的第二资源条");

  row(s, M, 1.75, 6.0, PRIMARY, "怒", "造成伤害即积累士气", "对逃生者每造成 1 点实际伤害，全队士气 +1");
  row(s, M, 2.62, 6.0, PRIMARY, "赏", "五连超级抽奖", "士气达 50/100/200/300/500/700/1000（封顶）时，每个在线猎人各触发一次七连抽奖");
  row(s, M, 3.49, 6.0, PRIMARY, "条", "量表占用经验条", "进度 = 距下一档士气，等级 = 已达成档数");
  row(s, M, 4.36, 6.0, PRIMARY, "护", "龙的领域对猎人关闭", "末影龙与末影人不会攻击猎人，其伤害被完全吸收");

  // 动态档位表
  s.addText("动态性能档位（按 猎人 : 逃生者 人数比实时切换）", { x: 7.0, y: 1.72, w: 5.8, h: 0.34, fontSize: 14, bold: true, color: TEXT, fontFace: F, margin: 0 });
  const th = { fill: { color: "201F1E" }, color: "FFFFFF", bold: true, fontSize: 11, valign: "middle" };
  const td = { fontSize: 11, color: TEXT, valign: "middle" };
  const tm = { fontSize: 10.5, color: MUTED, valign: "middle" };
  s.addTable([
    [{ text: "人数比", options: th }, { text: "猎人", options: th }, { text: "逃生者（括号为末地）", options: th }],
    [{ text: "≤1:1", options: td }, { text: "60血 速度2 急迫2", options: td }, { text: "60(80)血 抗性1 急迫2 速2 跳2", options: tm }],
    [{ text: "≤2:1", options: td }, { text: "40血 速度2 急迫2", options: td }, { text: "60(80)血 抗性1 急迫2 速1 跳2", options: tm }],
    [{ text: "≤3:1", options: td }, { text: "40血 速度1 急迫2", options: td }, { text: "60(100)血 抗性1 急迫2 速1 跳2", options: tm }],
    [{ text: ">3:1", options: td }, { text: "40血 速度1 急迫2", options: td }, { text: "80(100)血 抗性1(2) 急迫2 速1 跳2", options: tm }],
  ], { x: 7.0, y: 2.16, w: 5.8, colW: [1.05, 2.0, 2.75], border: { pt: 0.5, color: HAIR }, fill: { color: "FFFFFF" }, fontFace: F, rowH: 0.42, margin: 0.06 });
  s.addText("人数劣势方自动获得强化，开局与换人都会即时重算并全服广播。",
    { x: 7.0, y: 4.62, w: 5.8, h: 0.55, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });

  hairline(s, M, 5.95, 12.23);
  s.addText("士气的代价设计：猎人越凶，逃生者越危险——追杀本身就会喂饱猎人的军火库。",
    { x: M, y: 6.15, w: 12.2, h: 0.35, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S8 对抗规则速览 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "RULES 对抗规则", "细节里藏着博弈");

  row(s, M, 1.8, 6.0, PRIMARY, "箱", "死亡不掉落", "双方死亡均无掉落物；猎人装备原样保留到复活，逃生者淘汰转旁观");
  row(s, M, 2.78, 6.0, PRIMARY, "血", "击杀猎人回复 20%", "逃生者击杀猎人后，击杀者立即恢复自身最大生命的 20%");
  row(s, M, 3.76, 6.0, PRIMARY, "罗", "双罗盘信息战", "猎人追踪罗盘右键循环锁定目标；技能卡“盲点”可让罗盘乱指 60 秒");
  row(s, M, 4.74, 6.0, PRIMARY, "盾", "开局公平保护", "60 秒逃跑窗内猎人被定身且无法伤害逃生者，双方站立在同一起跑线");

  // 右侧：追踪罗盘示意卡
  s.addShape(pres.shapes.RECTANGLE, { x: 7.1, y: 1.8, w: 5.68, h: 3.9, fill: { color: TINT } });
  s.addText("罗盘博弈示例", { x: 7.38, y: 2.05, w: 5.1, h: 0.38, fontSize: 15, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText([
    { text: "猎人开局获得追踪罗盘，右键在多名逃生者间循环切换目标", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "逃生者抽到“盲点”后，猎人罗盘将持续指向随机假坐标", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "检查点罗盘永远指向当前目标——被干扰的只有猎人", options: { bullet: { code: "25B8", indent: 10 } } },
  ], { x: 7.38, y: 2.55, w: 5.15, h: 2.9, fontSize: 12.5, color: TEXT, fontFace: F, paraSpaceAfter: 12, margin: 0, valign: "top" });

  s.addText("所有规则数值集中在配置文件中，欢迎实测后提出调整建议。",
    { x: M, y: 6.2, w: 12.2, h: 0.35, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S9 技能卡系统 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "SKILL CARDS 技能卡", "与 SkillCards 模组联动的构筑层");

  // 概率比例条
  s.addText("抽卡概率", { x: M, y: 1.68, w: 3, h: 0.32, fontSize: 14, bold: true, color: TEXT, fontFace: F, margin: 0 });
  const probs = [["普通 68%", G.COMMON, 6.8], ["稀有 20%", G.RARE, 2.0], ["黑卡 10%", G.BLACK, 1.0], ["彩卡 2%", G.RAINBOW, 0.4]];
  let px = M;
  const barW = 12.23, barY = 2.06, barH = 0.5;
  probs.forEach(([label, color, w]) => {
    const segW = barW * (w / 10);
    s.addShape(pres.shapes.RECTANGLE, { x: px, y: barY, w: segW, h: barH, fill: { color } });
    if (w > 0.9) s.addText(label, { x: px + 0.08, y: barY, w: segW - 0.16, h: barH, fontSize: 11.5, bold: true, color: "FFFFFF", fontFace: F, valign: "middle", margin: 0 });
    px += segW;
  });
  s.addText("彩卡 2%", { x: px - 1.18, y: barY + 0.54, w: 1.16, h: 0.26, fontSize: 10.5, bold: true, color: G.RAINBOW, fontFace: F, align: "right", margin: 0 });

  row(s, M, 3.05, 6.0, G.RAINBOW, "抽", "检查点 = 抽卡点", "每激活一个检查点，激活者抽一张技能卡；要塞检查点固定抽到彩卡");
  row(s, M, 3.93, 6.0, PRIMARY, "槽", "技能栏：快捷栏最右格", "所有技能卡收进技能库无限保存；右键使用 · 左键切换 · 不可丢弃，冷却与可用状态由边框区分");
  row(s, M, 4.81, 6.0, PRIMARY, "合", "合成栏直接附魔", "附魔书 + 装备放进合成格即可附魔，同名同级 +1、异级取高，无需铁砧与经验");

  s.addShape(pres.shapes.RECTANGLE, { x: 7.35, y: 3.0, w: 5.43, h: 2.62, fill: { color: TINT_BLUE } });
  s.addText("超级抽奖", { x: 7.62, y: 3.22, w: 4.9, h: 0.36, fontSize: 15, bold: true, color: PRIMARY, fontFace: F, margin: 0 });
  s.addText([
    { text: "检查点激活：全体逃生者各获得一次七连抽奖，不限里程奖池", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "士气达标：全体在线猎人各触发一次", options: { bullet: { code: "25B8", indent: 10 }, breakLine: true } },
    { text: "奖励停留在界面，标记哪些拿哪些——未标记视为放弃", options: { bullet: { code: "25B8", indent: 10 } } },
  ], { x: 7.62, y: 3.68, w: 4.95, h: 1.8, fontSize: 12, color: TEXT, fontFace: F, paraSpaceAfter: 9, margin: 0, valign: "top" });

  s.addText("未安装 SkillCards 时主玩法完整可玩，仅跳过抽卡环节。",
    { x: M, y: 6.35, w: 12.2, h: 0.35, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S10 技能卡一览 =================
{
  const s = pres.addSlide();
  s.background = { color: BG };
  contentHeader(s, "SKILL CARDS 技能卡一览", "14 张卡，四种命运");
  const cards = [
    ["普通卡", "响玉", "恢复少量生命：生命恢复 4，持续 8 秒", "3min"],
    ["普通卡", "纱幕", "黑雾遮蔽：周围 20 格猎人缓慢 2 与失明 10 秒", "3min"],
    ["普通卡", "绽放", "弹开周围猎人，生命提升 5 与伤害吸收 5", "3min"],
    ["普通卡", "神罚", "下一次攻击造成 20 点伤害，持续 30 秒", "3min"],
    ["普通卡", "虹星", "向前快速突进，速度 4 持续 5 秒", "2min"],
    ["稀有卡", "达芙妮之灾厄", "召唤 20 只攻击猎人的杀手兔，击中即分裂", "8min"],
    ["稀有卡", "麒麟", "精准雷击两次周围 10 格的猎人", "3min"],
    ["稀有卡", "三世秘传", "随机窃取周围 200 格猎人背包内的一件物品", "5min"],
    ["稀有卡", "盲点", "干扰猎人罗盘 60 秒，自身隐身 30 秒", "5min"],
    ["彩卡", "海王之翼", "获得飞翔能力，持续 20 秒", "3min"],
    ["彩卡", "亚丝缇的赐福", "向 200–300 格外的随机位置传送", "5min"],
    ["黑卡", "诅咒化身", "力量 4 持续 60 秒，期间最大生命减半", "5min"],
    ["黑卡", "圣树化身", "生命恢复 3 持续 60 秒，期间虚弱 2", "5min"],
    ["黑卡", "赤鳞之跃动", "分段损失 20 点生命（保留 2 点），获得速度 2 / 力量 2 / 夜视 / 急迫 3 / 跳跃提升 1，持续 30 秒", "4min"],
  ];
  const gc = { "普通卡": G.COMMON, "稀有卡": G.RARE, "彩卡": G.RAINBOW, "黑卡": G.BLACK };
  const th = { fill: { color: "201F1E" }, color: "FFFFFF", bold: true, fontSize: 11.5, valign: "middle" };
  const rows = [[
    { text: "稀有度", options: th }, { text: "卡名", options: th }, { text: "效果", options: th }, { text: "冷却", options: th },
  ]];
  cards.forEach(([grade, name, desc, cd]) => {
    rows.push([
      { text: grade, options: { fontSize: 11, bold: true, color: gc[grade], valign: "middle" } },
      { text: name, options: { fontSize: 11, bold: true, color: TEXT, valign: "middle" } },
      { text: desc, options: { fontSize: 11, color: TEXT, valign: "middle" } },
      { text: cd, options: { fontSize: 11, color: MUTED, valign: "middle", align: "center" } },
    ]);
  });
  s.addTable(rows, {
    x: M, y: 1.55, w: 12.23, colW: [1.15, 1.75, 8.03, 1.3],
    border: { pt: 0.5, color: HAIR }, fill: { color: "FFFFFF" }, fontFace: F, rowH: 0.335, margin: 0.05,
  });
  s.addText("抽卡概率：普通 68% · 稀有 20% · 黑卡 10% · 彩卡 2%（可在配置中调整）",
    { x: M, y: 6.85, w: 12.2, h: 0.32, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
}

// ================= S11 Demo CTA（深色） =================
{
  const s = pres.addSlide();
  s.background = { color: DARK };
  s.addText("DEMO OPEN 试玩开放", { x: M, y: 1.15, w: 9, h: 0.35, fontSize: 13, bold: true, color: BLUE_LT, fontFace: F, charSpacing: 3, margin: 0 });
  s.addText([
    { text: "9 月 26 日", options: { color: ACCENT === "107C10" ? "6CCB5F" : "FFFFFF" } },
    { text: "  Demo 开放试玩", options: { color: "FFFFFF" } },
  ], { x: M, y: 1.55, w: 12.2, h: 1.0, fontSize: 44, bold: true, fontFace: F, margin: 0 });
  s.addText("带上你的朋友——一个当猎人，其余人逃。",
    { x: M, y: 2.72, w: 11, h: 0.45, fontSize: 17, color: "C8C6C4", fontFace: F, margin: 0 });

  s.addText("三步上手", { x: M, y: 3.6, w: 4, h: 0.36, fontSize: 15, bold: true, color: "FFFFFF", fontFace: F, margin: 0 });
  const steps = [
    ["1", "安装 NeoForge 26.2 实例", "Minecraft Java 26.2 + NeoForge 26.2"],
    ["2", "放入两个模组 jar", "manhunt + skillcards（技能卡可选）"],
    ["3", "开局一条指令", "/manhunt admin start random 1"],
  ];
  steps.forEach(([n, h, d], i) => {
    const x = M + i * 4.15;
    s.addShape(pres.shapes.RECTANGLE, { x, y: 4.05, w: 0.5, h: 0.5, fill: { color: PRIMARY } });
    s.addText(n, { x, y: 4.04, w: 0.5, h: 0.5, fontSize: 20, bold: true, color: "FFFFFF", fontFace: "Segoe UI", align: "center", valign: "middle", margin: 0 });
    s.addText(h, { x, y: 4.68, w: 3.9, h: 0.34, fontSize: 14.5, bold: true, color: "FFFFFF", fontFace: F, margin: 0 });
    s.addText(d, { x, y: 5.02, w: 3.9, h: 0.34, fontSize: 11.5, color: "C8C6C4", fontFace: F, margin: 0 });
  });

  hairline(s, M, 5.85, 12.23);
  s.addText([
    { text: "开源仓库  ", options: { bold: true, color: "FFFFFF" } },
    { text: "github.com/TakaraMiyuki/Manhunt  ·  TakaraMiyuki/SkillCards", options: { color: "C8C6C4" } },
  ], { x: M, y: 6.05, w: 12.2, h: 0.35, fontSize: 12.5, fontFace: F, margin: 0 });
  s.addText("单人也可体验：/manhunt admin start solo 无猎人调试模式 · 数值与机制欢迎实测反馈",
    { x: M, y: 6.45, w: 12.2, h: 0.35, fontSize: 11.5, color: "979491", fontFace: F, margin: 0 });
}

pres.writeFile({ fileName: "F:/ZcodeBuild/Manhunt/docs/Manhunt模组宣传.pptx" }).then(() => console.log("PPTX done"));
