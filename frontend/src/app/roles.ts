import type { Role } from '../api/types'

export function homeFor(role: Role): string {
  return role === 'LANDLORD' ? '/l' : '/t'
}
