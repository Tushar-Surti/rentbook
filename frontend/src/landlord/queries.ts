import { api } from '../api/client'
import type { LandlordBoard, Payouts, Property } from '../api/types'

export const boardQuery = {
  queryKey: ['board'],
  queryFn: () => api<LandlordBoard>('/dashboard/landlord'),
}

export const payoutsQuery = {
  queryKey: ['payouts'],
  queryFn: () => api<Payouts>('/payouts/account'),
}

export function propertyQuery(propertyId: string) {
  return {
    queryKey: ['property', propertyId],
    queryFn: () => api<Property>(`/properties/${propertyId}`),
  }
}
