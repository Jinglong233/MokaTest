import {Environment} from "@/types/domain/api/Environment";
import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";


export class AddEnvDTO extends Environment {

    constructor() {
        super();
        this.id = undefined;
        this.envName = '';
        this.teamId = undefined;
        this.cookies = [];
        this.headers = [];
        this.envVar = [];
        this.serve = undefined;
        this.dbs = undefined;
        this.createTime = undefined;
        this.createUserId = undefined;
        this.updateTime = undefined;
        this.updateUserId = undefined;
    }
}