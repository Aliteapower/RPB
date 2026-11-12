import type {RouteRecordRaw} from 'vue-router'
export const catalogRoutes:RouteRecordRaw[]=[{path:'/stores/:storeId/catalog',name:'product-catalog',component:()=>import('./CatalogPage.vue')}]
