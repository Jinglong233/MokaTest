export class ServeParameter {
  // id
  id?: number | null;
  // 服务名称
  name?: string;
  // 服务地址
  address?: string;
  constructor() {
    this.id = null;
    this.name = '';
    this.address = '';
  }
}
