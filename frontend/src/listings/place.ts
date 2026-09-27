import type { ListingPlace } from '../api/types'

/** "Bed A, Room 201, Sunrise PG": the unit as the register names it. */
export function placeName(place: ListingPlace): string {
  const unit = place.roomLabel ? `${place.unitLabel}, ${place.roomLabel}` : place.unitLabel
  return `${unit}, ${place.propertyName}`
}

/** What is on offer, as someone looking for a room would say it. */
export function offerName(place: ListingPlace): string {
  switch (place.unitKind) {
    case 'BED':
      return place.bedsInRoom > 1
        ? `A bed in a ${place.bedsInRoom}-sharing room at ${place.propertyName}`
        : `A bed at ${place.propertyName}`
    case 'ROOM':
      return `A room at ${place.propertyName}`
    default:
      return `${place.unitLabel} at ${place.propertyName}`
  }
}
