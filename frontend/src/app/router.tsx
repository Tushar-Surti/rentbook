import type { ComponentType } from 'react'
import { createBrowserRouter } from 'react-router'
import { ForgotPasswordPage } from '../auth/ForgotPasswordPage'
import { LoginPage } from '../auth/LoginPage'
import { RegisterPage } from '../auth/RegisterPage'
import { AcceptInvitePage } from '../invite/AcceptInvitePage'
import { LandlordLayout } from '../landlord/LandlordLayout'
import { TenantLayout } from '../tenant/TenantLayout'
import { NotFound, RouteError } from './errors'
import { GuestOnly, HomeRedirect, RequireRole, SessionLoading } from './guards'

/**
 * Each screen behind the role gates loads on first visit, so a tenant's phone never downloads the
 * landlord's book, and signing in stays light.
 */
function screen<T>(load: () => Promise<T>, pick: (module: T) => ComponentType) {
  return async () => ({ Component: pick(await load()) })
}

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    hydrateFallbackElement: <SessionLoading />,
    children: [
      { path: '/', element: <HomeRedirect /> },
      { path: '/login', element: <GuestOnly><LoginPage /></GuestOnly> },
      { path: '/register', element: <GuestOnly><RegisterPage /></GuestOnly> },
      { path: '/forgot-password', element: <GuestOnly><ForgotPasswordPage /></GuestOnly> },
      { path: '/invite/:token', element: <AcceptInvitePage /> },
      { path: '/r/:slug', lazy: screen(() => import('../listings/PublicListingPage'), (m) => m.PublicListingPage) },
      {
        path: '/caretaker-invite/:token',
        lazy: screen(() => import('../caretaker/CaretakerAcceptPage'), (m) => m.CaretakerAcceptPage),
      },
      {
        path: '/c',
        lazy: async () => {
          const { CaretakerLayout } = await import('../caretaker/CaretakerLayout')
          return {
            Component: () => (
              <RequireRole role="CARETAKER">
                <CaretakerLayout />
              </RequireRole>
            ),
          }
        },
        children: [
          { index: true, lazy: screen(() => import('../caretaker/CaretakerLayout'), (m) => m.CaretakerIndex) },
          { path: 'p/:propertyId', lazy: screen(() => import('../caretaker/CaretakerPages'), (m) => m.CaretakerBookPage) },
          {
            path: 'p/:propertyId/leases/:leaseId',
            lazy: screen(() => import('../caretaker/CaretakerPages'), (m) => m.CaretakerLeasePage),
          },
          {
            path: 'p/:propertyId/requests/:ticketId',
            lazy: screen(() => import('../caretaker/CaretakerPages'), (m) => m.CaretakerRequestPage),
          },
          {
            path: 'requests/:ticketId',
            lazy: screen(() => import('../caretaker/CaretakerPages'), (m) => m.CaretakerRequestPage),
          },
        ],
      },
      {
        path: '/l',
        element: <RequireRole role="LANDLORD"><LandlordLayout /></RequireRole>,
        children: [
          { index: true, lazy: screen(() => import('../landlord/BookIndex'), (m) => m.BookIndex) },
          { path: 'properties/new', lazy: screen(() => import('../landlord/NewPropertyPage'), (m) => m.NewPropertyPage) },
          { path: 'payouts', lazy: screen(() => import('../landlord/PayoutsPage'), (m) => m.PayoutsPage) },
          { path: 'caretakers', lazy: screen(() => import('../landlord/CaretakersPage'), (m) => m.CaretakersPage) },
          { path: 'listings', lazy: screen(() => import('../listings/ListingsPage'), (m) => m.ListingsPage) },
          { path: 'listings/:listingId', lazy: screen(() => import('../listings/ListingsPage'), (m) => m.ListingPage) },
          {
            path: 'p/:propertyId/units/:unitId/list',
            lazy: screen(() => import('../listings/ListingsPage'), (m) => m.NewListingPage),
          },
          { path: 'p/:propertyId', lazy: screen(() => import('../landlord/BookPage'), (m) => m.BookPage) },
          // Nested under the property so its index tab stays marked.
          {
            path: 'p/:propertyId/units/:unitId/invite',
            lazy: screen(() => import('../landlord/InvitePage'), (m) => m.InvitePage),
          },
          {
            path: 'p/:propertyId/leases/:leaseId',
            lazy: screen(() => import('../landlord/LeasePage'), (m) => m.LeasePage),
          },
          {
            path: 'p/:propertyId/leases/:leaseId/condition/:reportId',
            lazy: screen(() => import('../condition/ConditionReportPage'), (m) => m.LandlordConditionPage),
          },
          {
            path: 'p/:propertyId/requests/:ticketId',
            lazy: screen(() => import('../landlord/RequestPage'), (m) => m.LandlordRequestPage),
          },
          // Where the new-request email points: the thread, without knowing which property it's on.
          {
            path: 'requests/:ticketId',
            lazy: screen(() => import('../landlord/RequestPage'), (m) => m.LandlordRequestPage),
          },
        ],
      },
      {
        path: '/t',
        element: <RequireRole role="TENANT"><TenantLayout /></RequireRole>,
        children: [
          { index: true, lazy: screen(() => import('../tenant/SlipPage'), (m) => m.SlipPage) },
          { path: 'rent', lazy: screen(() => import('../tenant/RentPage'), (m) => m.RentPage) },
          { path: 'requests', lazy: screen(() => import('../tenant/RequestsPage'), (m) => m.RequestsPage) },
          { path: 'requests/new', lazy: screen(() => import('../tenant/NewRequestPage'), (m) => m.NewRequestPage) },
          {
            path: 'requests/:ticketId',
            lazy: screen(() => import('../tenant/RequestPage'), (m) => m.TenantRequestPage),
          },
          { path: 'documents', lazy: screen(() => import('../tenant/DocumentsPage'), (m) => m.DocumentsPage) },
          {
            path: 'condition/:reportId',
            lazy: screen(() => import('../condition/ConditionReportPage'), (m) => m.TenantConditionPage),
          },
        ],
      },
      { path: '*', element: <NotFound /> },
    ],
  },
])
