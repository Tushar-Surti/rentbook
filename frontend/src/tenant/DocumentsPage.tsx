import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { VaultEntry } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Sheet } from '../design/Sheet'
import { DocumentShelf } from '../documents/DocumentShelf'
import { FileDocument } from '../documents/FileDocument'
import { vaultQuery } from '../documents/queries'
import { firstName } from '../maintenance/labels'
import styles from './DocumentsPage.module.css'
import { tenantHomeQuery } from './queries'

/** The tenant's shelf of papers for this lease: what's on file, and filing the next one. */
export function DocumentsPage() {
  const queryClient = useQueryClient()
  const home = useQuery(tenantHomeQuery)
  const lease = home.data?.lease ?? null
  const vault = useQuery({ ...vaultQuery(lease?.id ?? ''), enabled: Boolean(lease) })

  if (home.isPending) return <SessionLoading />
  if (!lease) {
    return (
      <div className={styles.page}>
        <h1 className={styles.heading}>No active lease</h1>
      </div>
    )
  }
  const landlord = firstName(lease.landlord.fullName)
  const remove = async (entry: VaultEntry) => {
    await api(`/documents/${entry.id}`, { method: 'DELETE' })
    await queryClient.invalidateQueries({ queryKey: ['vault', lease.id] })
  }

  return (
    <div className={styles.page}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>Documents</h1>
        <p className={styles.lede}>
          Your lease agreement, your ID, and anything else you and {landlord} keep together. {landlord} sees what you
          file here. Receipts are on the Rent tab.
        </p>
      </div>
      <Sheet className={styles.sheet} aria-labelledby="shelf-heading">
        <h2 id="shelf-heading" className={styles.sheetHeading}>
          On file
        </h2>
        {vault.data ? (
          <DocumentShelf entries={vault.data} label="Documents on your lease" onRemove={remove} />
        ) : (
          <SessionLoading />
        )}
      </Sheet>
      <section className={styles.section} aria-labelledby="file-heading">
        <h2 id="file-heading" className={styles.sectionHeading}>
          File a document
        </h2>
        <FileDocument leaseId={lease.id} viewer="TENANT" prominent />
      </section>
    </div>
  )
}
