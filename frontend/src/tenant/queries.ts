import { api } from '../api/client'
import type { TenantHome } from '../api/types'

export const tenantHomeQuery = {
  queryKey: ['tenant-home'],
  queryFn: () => api<TenantHome>('/dashboard/tenant'),
}
