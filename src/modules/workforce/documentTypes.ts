export interface DocumentType {id:string;name:string;enabled:boolean;version:number}
export interface DocumentAttachment {id:string;filename:string;contentType:string;size:number}
export interface EmployeeDocument {id:string;typeId:string;typeName:string;number:string;holderName?:string;approvedOn?:string|null;prEffectiveOn?:string|null;expiresOn:string|null;notes:string;version:number;attachments:DocumentAttachment[]}
export interface DocumentPage {types:DocumentType[];items:EmployeeDocument[]}
export interface DocumentRecognition {documentId:string;attachmentId:string;documentVersion:number;name:string|null;number:string|null;approvedOn:string|null;expiresOn:string|null;prDate:string|null;warnings:string[]}

export interface DocumentPreviewSource {path?:string;file?:File;filename:string;contentType:string}
