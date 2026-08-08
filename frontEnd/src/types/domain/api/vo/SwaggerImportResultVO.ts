export interface SkippedOperationVO {
  path: string;
  method: string;
  reason: string;
}

export interface SwaggerImportResultVO {
  folderCount: number;
  interfaceCount: number;
  skippedCount: number;
  overwrittenCount: number;
  skipped: SkippedOperationVO[];
}
