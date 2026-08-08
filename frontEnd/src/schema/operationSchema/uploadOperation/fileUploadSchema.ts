import {z} from "zod";
import {elementSchema} from "@/schema/operationSchema/operation";

const fileSchema = z
    .instanceof(File) // 校验是否为 File 对象
    .refine((file) => file.size > 0, "文件不能为空")
    .refine(
        (file) => file.size <= 5 * 1024 * 1024,
        "文件大小不能超过 5MB"
    )
    .refine(
        (file) => ["image/jpeg", "image/png"].includes(file.type),
        "仅支持 JPEG 或 PNG 格式"
    );


// 文件上传
const fileUploadSchema = z.object({
    element: elementSchema,
    file: fileSchema
});


export {fileUploadSchema}