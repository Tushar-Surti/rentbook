import { api, ApiError } from '../api/client'
import type { DocumentType, DocumentVisibility, UploadTicket } from '../api/types'

/**
 * Sends a file straight to storage. The API signs an upload URL, the browser PUTs the file there, and
 * the API then checks the bucket before the file can be used. Returns the document's id.
 */
export async function uploadFile(
  file: File,
  leaseId: string,
  type: DocumentType,
  visibility?: DocumentVisibility,
): Promise<string> {
  const ticket = await api<UploadTicket>('/uploads', {
    method: 'POST',
    json: { leaseId, type, filename: file.name, contentType: file.type, sizeBytes: file.size, visibility },
  })
  return sendAndConfirm(ticket, file)
}

/** A photo for one line of a move-in or move-out report; it belongs to that line from the start. */
export async function uploadConditionPhoto(file: File, lineId: string): Promise<string> {
  const ticket = await api<UploadTicket>(`/condition-lines/${lineId}/photos`, {
    method: 'POST',
    json: { filename: file.name, contentType: file.type, sizeBytes: file.size },
  })
  return sendAndConfirm(ticket, file)
}

async function sendAndConfirm(ticket: UploadTicket, file: File): Promise<string> {
  let stored: Response
  try {
    stored = await fetch(ticket.url, { method: ticket.method, headers: ticket.headers, body: file })
  } catch {
    throw new ApiError(0, { detail: "Couldn't reach storage. Check your connection and try again." })
  }
  if (!stored.ok) {
    throw new ApiError(stored.status, { detail: "The file didn't reach storage. Try again." })
  }
  await api(`/documents/${ticket.documentId}/complete`, { method: 'POST' })
  return ticket.documentId
}
