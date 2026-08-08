import {z} from "zod";
import {elementSchema} from "@/schema/operationSchema/operation";
import {SwitchIframeType} from "@/types/enum/iframe/SwitchIframeType";


// iframe操作
export const iframeSchema = z.object({
    switchIframeType: z.nativeEnum(SwitchIframeType), // iframe切换类型
    element: elementSchema.optional(), // iframe元素
    url: z.string().optional(), // iframe的url
    iframeName: z.string().optional(), // iframe的name
    iframeIndex: z.number().optional(), // iframe的index
    iframeId: z.string().optional() // iframe的id
}).superRefine((data, ctx) => {
    if (data.switchIframeType === SwitchIframeType.ELEMENT) {
        if (!data.element) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "请选择iframe元素",
                path: ["element"],
            });
        }
    } else if (data.switchIframeType === SwitchIframeType.ID) {
        if (!data.iframeId) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "请填写id",
                path: ["iframeId"],
            });
        }
    } else if (data.switchIframeType === SwitchIframeType.INDEX) {
        if (!data.iframeIndex) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "请填写切换的索引",
                path: ["iframeIndex"],
            });
        }
    } else if (data.switchIframeType === SwitchIframeType.NAME) {
        if (!data.iframeName) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "请填写iframe的name",
                path: ["iframeName"],
            });
        }
    } else if (data.switchIframeType === SwitchIframeType.URL) {
        if (!data.url) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: "请填写iframe的URL",
                path: ["url"],
            });
        }
    }
});

