import { BasicColumn, FormSchema } from '/@/components/Table';

export const statusTag = (colorMap: Record<string, string>) => ({ text }) => {
  return text ? { color: colorMap[text] || 'default', text } : '';
};

export const keywordSearch = (field = 'name', label = '名称'): FormSchema[] => [
  { label, field, component: 'JInput' },
];

export const commonTimeColumns: BasicColumn[] = [
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '更新时间', dataIndex: 'updateTime', width: 170 },
];

export const readonlyPathColumn = (title = '路径'): BasicColumn => ({
  title,
  dataIndex: 'path',
  ellipsis: true,
  width: 320,
});
