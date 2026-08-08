import {ApiRequest} from "@/types/domain/api/ApiRequest";

export class ApiRequestVO extends ApiRequest {
    children?: ApiRequestVO[];
}
