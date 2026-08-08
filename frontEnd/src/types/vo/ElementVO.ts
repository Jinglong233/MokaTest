import {Element} from "@/types/domain/Element";

export interface ElementVO extends Element {
    children?: ElementVO[],
}