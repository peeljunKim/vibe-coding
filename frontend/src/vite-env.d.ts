/// <reference types="vite/client" />

// Vite 환경과 Asset 타입 정의
interface ViteTypeOptions {
  strictImportMetaEnv: unknown
}

interface ImportMetaEnv {
  readonly VITE_BACKEND_BASE_URL?: string
}
