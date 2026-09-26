-- Payments the landlord records by hand (cash, UPI to their own account, a bank transfer, a cheque),
-- next to the ones Razorpay confirms. A recorded payment carries no platform fee.
alter table payments
    add column method varchar(16) not null default 'RAZORPAY'
        check (method in ('RAZORPAY', 'CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    add column received_on date,
    add column note varchar(200),
    add column recorded_by uuid references users (id);
