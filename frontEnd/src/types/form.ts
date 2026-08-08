import type {z} from 'zod';

export interface FormField {
    key: string;
    label: string;
    type: 'input' | 'select' | 'checkbox' | 'element';
    placeholder?: string;
    required?: boolean;
    options?: Array<{ label: string; value: any }>;
    conditions?: Array<{
        field: string;
        type: 'equals' | 'notEquals' | 'includes' | 'notIncludes' | 'in';
        value?: any;
        values?: any[];
    }>;
};

export interface FormConfig<T extends z.ZodObject<any>>{
    fields: FormField[];
    schema: T;
    initialData?: Partial<z.infer<T>>;
};