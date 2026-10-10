/** 评审检查表文案 — ReviewStreaming + ReviewResult 共享 */
import i18n from '@/locale';

function t(key: string): string {
  return i18n.global.t(key);
}

/** 检查项文案：按 id 取词条，缺词条时回退到 id 本身 */
export function checklistText(id: string): string {
  const key = `qa.ai.review.checklist.${id}`;
  const v = t(key);
  return v === key ? id : v;
}

const DIM_KEYS = ['completeness', 'clarity', 'testability', 'consistency', 'security', 'boundary'];

/** 维度清单（label 随当前语言环境取值） */
export function getDims() {
  return DIM_KEYS.map((key) => ({ key, label: t(`qa.ai.review.dim.${key}`) }));
}

export function dimOfId(id: string): string {
  if (id.startsWith('CMP')) return 'completeness';
  if (id.startsWith('CLR')) return 'clarity';
  if (id.startsWith('TST')) return 'testability';
  if (id.startsWith('CON')) return 'consistency';
  if (id.startsWith('SEC')) return 'security';
  if (id.startsWith('BND')) return 'boundary';
  return '';
}

export function answerColor(answer: string) {
  if (answer === 'YES') return 'green';
  if (answer === 'PARTIAL') return 'orange';
  return 'red';
}

export function answerText(answer: string) {
  if (answer === 'YES') return t('qa.ai.review.answer.yes');
  if (answer === 'PARTIAL') return t('qa.ai.review.answer.partial');
  return t('qa.ai.review.answer.no');
}

export function scoreColorStr(score: number | undefined | null) {
  if (score == null) return '';
  if (score >= 70) return 'rgb(var(--green-6))';
  if (score >= 50) return 'rgb(var(--orange-6))';
  return 'rgb(var(--red-6))';
}

export function scoreTagColor(score: number | null | undefined) {
  if (score == null) return 'gray';
  if (score >= 70) return 'green';
  if (score >= 50) return 'orange';
  return 'red';
}

export function levelColor(level: string) {
  if (level === 'HIGH') return 'red';
  if (level === 'MEDIUM') return 'orange';
  return 'green';
}

export function levelText(level: string) {
  if (level === 'HIGH') return t('qa.ai.review.level.high');
  if (level === 'MEDIUM') return t('qa.ai.review.level.medium');
  return t('qa.ai.review.level.low');
}
