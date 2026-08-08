// schema注册中心

import {closePageSchema} from "@/schema/operationSchema/browserOperation/closePageSchema";
import {assertSchema} from "@/schema/operationSchema/assertOperation/AssertSchema";
import {openPageSchema} from "@/schema/operationSchema/browserOperation/openPageSchema";
import {switchTabSchema} from "@/schema/operationSchema/browserOperation/switchTabSchema";
import {clickSchema} from "@/schema/operationSchema/clickOperation/clickSchema";
import {whileCycleSchema} from "@/schema/operationSchema/cycleOperaion/whileCycleSchema";
import {forCycleSchema} from "@/schema/operationSchema/cycleOperaion/forCycleSchema";
import {extractSchema} from "@/schema/operationSchema/extractOperation/extractSchema";
import {ifSchema} from "@/schema/operationSchema/ifOperation/IfSchema";
import {iframeSchema} from "@/schema/operationSchema/iframeOperation/iframeSchema";
import {keyboardInputSchema} from "@/schema/operationSchema/keyboardOperation/keyboardInputSchema";
import {fileUploadSchema} from "@/schema/operationSchema/uploadOperation/fileUploadSchema";
import {waitFixedDurationSchema} from "@/schema/operationSchema/waitOperation/waitFixedDurationSchema";

// 集中注册所有 schema
export const SCHEMA_REGISTRY = {
    "ASSERT": assertSchema,  // 通过字符串标识 "openPage" 查找
    "CLOSE_PAGE": closePageSchema,
    "OPEN_PAGE": openPageSchema,
    "SWITCH_TAB": switchTabSchema,
    "CLICK": clickSchema,
    "FOR": forCycleSchema,
    "WHILE": whileCycleSchema,
    "EXTRACT": extractSchema,
    "IF": ifSchema,
    "IFRAME": iframeSchema,
    "KEYBOARD": keyboardInputSchema,
    "FILE_UPLOAD": fileUploadSchema,
    "WAIT": waitFixedDurationSchema,
} as const;

// 类型：自动推断所有可用的标识
export type SchemaKey = keyof typeof SCHEMA_REGISTRY;