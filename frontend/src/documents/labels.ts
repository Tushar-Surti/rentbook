import type { DocumentPush, DocumentType } from '../api/types'
import { firstName } from '../maintenance/labels'

export const TYPE_LABELS: Record<DocumentType, string> = {
  LEASE: 'Lease agreement',
  KYC: 'ID document',
  RECEIPT: 'Receipt',
  TICKET_PHOTO: 'Photo',
  OTHER: 'Other',
}

const FILED: Record<DocumentType, string> = {
  LEASE: 'the lease agreement',
  KYC: 'an ID document',
  RECEIPT: 'a receipt',
  TICKET_PHOTO: 'a photo',
  OTHER: 'a document',
}

/** The other party's live line: "Lata filed the lease agreement: agreement.pdf." */
export function filedLine(push: DocumentPush): string {
  return `${firstName(push.actor)} filed ${FILED[push.type]}: ${push.filename}.`
}

export const ACCEPTED_FILES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
export const MAX_FILE_BYTES = 10 * 1024 * 1024
