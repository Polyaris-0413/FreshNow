#!/usr/bin/env node
/*
 * 从 seed 生成 app/src/main/java/com/freshnow/app/ui/theme/Color.kt。
 *
 * 用法（在项目根目录执行）：
 *   npm install @material/material-color-utilities@0.3.0
 *   node tools/generate-color-scheme.cjs '#4285F4'
 *
 * node_modules 与 npm 生成的 package.json 已在 .gitignore 里，不影响 Android 构建。
 *
 * 为何固定 0.3.0：它开箱可用（0.4.0 的 ESM 打包缺少 .js 后缀，Node 严格模式加载不了）。
 * 已核对 0.3.0 与 0.4.0 对同一 seed 输出完全一致，故用前者不必给依赖打补丁。
 *
 * 生成后必须同步 app/src/main/res/values/themes.xml 与 values-night/themes.xml 里的
 * @color/window_background（脚本会把该值打印出来），否则启动图与首帧之间会出现色差；
 * ThemeWindowBackgroundTest 会断言两者相等。
 *
 * 设计取舍说明：
 * - 色调映射完全由生成器决定，不按 material-3 技能的映射表手改。技能那张表是设计参考，
 *   个别角色与实现并不一致（例如 background/surface 该实现取 neutral tone 99，技能表记的是 98）。
 * - surfaceContainer* 五档生成器不提供，按技能 color-system.md:122-128 / :140-146 明文给出的
 *   色调，从同一条 neutral 色调板取值。
 * - surfaceDim / surfaceBright / surfaceTint 生成器不提供、技能也未给出色调，故不生成，
 *   留待需要 tonalElevation 时再补，不臆造色值。
 */
const mcu = require('@material/material-color-utilities');
const fs = require('fs');
const path = require('path');

const SEED = process.argv[2] || '#4285F4';
const OUT = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'freshnow', 'app', 'ui', 'theme', 'Color.kt');

const hexOf = (v) => mcu.hexFromArgb(v).toUpperCase();
const theme = mcu.themeFromSourceColor(mcu.argbFromHex(SEED));
const light = theme.schemes.light.toJSON();
const dark = theme.schemes.dark.toJSON();

const PAL = { primary: 'Primary', secondary: 'Secondary', tertiary: 'Tertiary', neutral: 'Neutral', neutralVariant: 'NeutralVariant', error: 'Error' };

// 技能 color-system.md:122-128 / :140-146 明文给出的色调
const CONTAINER_TONES = {
  light: { surfaceContainerLowest: 100, surfaceContainerLow: 96, surfaceContainer: 94, surfaceContainerHigh: 92, surfaceContainerHighest: 90 },
  dark: { surfaceContainerLowest: 4, surfaceContainerLow: 10, surfaceContainer: 12, surfaceContainerHigh: 17, surfaceContainerHighest: 22 },
};

// 角色该取自哪条色调板。必须先定板再在板内找色调：高色调处 neutral 与 primary 会撞成同一色值，
// 按板顺序取首个命中会把 background 记成 Primary99 之类的错误出处。
const ROLE_PALETTE = {
  primary: 'primary', onPrimary: 'primary', primaryContainer: 'primary', onPrimaryContainer: 'primary', inversePrimary: 'primary',
  secondary: 'secondary', onSecondary: 'secondary', secondaryContainer: 'secondary', onSecondaryContainer: 'secondary',
  tertiary: 'tertiary', onTertiary: 'tertiary', tertiaryContainer: 'tertiary', onTertiaryContainer: 'tertiary',
  error: 'error', onError: 'error', errorContainer: 'error', onErrorContainer: 'error',
  background: 'neutral', onBackground: 'neutral', surface: 'neutral', onSurface: 'neutral',
  surfaceContainerLowest: 'neutral', surfaceContainerLow: 'neutral', surfaceContainer: 'neutral',
  surfaceContainerHigh: 'neutral', surfaceContainerHighest: 'neutral',
  inverseSurface: 'neutral', inverseOnSurface: 'neutral', scrim: 'neutral',
  surfaceVariant: 'neutralVariant', onSurfaceVariant: 'neutralVariant', outline: 'neutralVariant', outlineVariant: 'neutralVariant',
};

const ROLES = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'background', 'onBackground',
  'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer', 'surfaceContainerHigh', 'surfaceContainerHighest',
  'inverseSurface', 'inverseOnSurface',
  'outline', 'outlineVariant', 'scrim',
];

const consts = new Map();
const refs = { light: {}, dark: {} };
const unresolved = [];

function refFor(schemeName, role, hex) {
  const palName = ROLE_PALETTE[role];
  const pal = theme.palettes[palName];
  if (pal) {
    for (let t = 0; t <= 100; t++) {
      if (hexOf(pal.tone(t)) === hex) {
        consts.set(PAL[palName] + t, hex);
        return PAL[palName] + t;
      }
    }
  }
  unresolved.push(`${schemeName}.${role} = ${hex}（不在 ${palName} 板内）`);
  return `Color(${hex.replace('#', '0xFF')})`;
}

for (const schemeName of ['light', 'dark']) {
  const scheme = schemeName === 'light' ? light : dark;
  for (const role of ROLES) {
    if (CONTAINER_TONES[schemeName][role] !== undefined) {
      const tone = CONTAINER_TONES[schemeName][role];
      consts.set('Neutral' + tone, hexOf(theme.palettes.neutral.tone(tone)));
      refs[schemeName][role] = 'Neutral' + tone;
    } else if (scheme[role] !== undefined) {
      refs[schemeName][role] = refFor(schemeName, role, hexOf(scheme[role]));
    } else {
      unresolved.push(`${schemeName}.${role} 生成器未提供`);
    }
  }
}

const ORDER = ['Primary', 'Secondary', 'Tertiary', 'Neutral', 'NeutralVariant', 'Error'];
const constKeys = [...consts.keys()].sort((a, b) => {
  const pa = ORDER.findIndex((p) => a.startsWith(p));
  const pb = ORDER.findIndex((p) => b.startsWith(p));
  if (pa !== pb) return pa - pb;
  return parseInt(a.replace(/\D/g, ''), 10) - parseInt(b.replace(/\D/g, ''), 10);
});

const schemeText = (fn, name, refMap) =>
  `internal val ${name} = ${fn}(\n` +
  ROLES.filter((r) => refMap[r] !== undefined).map((r) => `    ${r} = ${refMap[r]},`).join('\n') +
  `\n)`;

const content = `package com.freshnow.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 本文件由 tools/generate-color-scheme.cjs 生成，请勿手改。
 *
 * seed：${SEED}
 * 生成器：@material/material-color-utilities@0.3.0 的 themeFromSourceColor（经典色调映射）
 *
 * 色调映射完全以生成器为准，不要按 material-3 技能的映射表手改：那张表是设计参考，
 * 与实现在个别角色上并不一致（例如 background/surface 该实现取 neutral tone 99，技能表记的是 98；
 * 深色 surface 该实现取 tone 10，技能表记的是 6）。本文件的值全部来自生成器。
 *
 * 重新生成：node tools/generate-color-scheme.cjs '<新 seed>'
 * 之后必须同步 res/values/themes.xml 与 res/values-night/themes.xml 的 @color/window_background
 * （脚本会打印该值）——页面底色与启动图底色共用该资源，ThemeWindowBackgroundTest 断言两者相等。
 *
 * 表面容器五档生成器不提供，按技能 color-system.md:122-128 / :140-146 明文给出的色调，
 * 从同一条 neutral 色调板取值。
 *
 * 有意未生成：surfaceDim、surfaceBright、surfaceTint —— 生成器不提供、技能也未给出色调，故不臆造。
 * 本项目未使用 tonalElevation，暂不影响观感；将来要用之前须先补上这三个角色。
 */

// 色调板取值，命名规则为 <色调板><色调>
${constKeys.map((k) => `internal val ${k} = Color(0xFF${consts.get(k).slice(1)})`).join('\n')}

${schemeText('lightColorScheme', 'LightColorScheme', refs.light)}

${schemeText('darkColorScheme', 'DarkColorScheme', refs.dark)}
`;

fs.writeFileSync(OUT, content);
console.log(`seed ${SEED} -> ${path.relative(process.cwd(), OUT)}`);
console.log(`色调常量 ${consts.size} 个`);
console.log(unresolved.length ? `未解决：\n   ${unresolved.join('\n   ')}` : '所有角色均已解决');
console.log('');
console.log('请同步主题（否则启动图与首帧有色差）：');
console.log(`  res/values/themes.xml        window_background = ${hexOf(light.background)}`);
console.log(`  res/values-night/themes.xml  window_background = ${hexOf(dark.background)}`);
