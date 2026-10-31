// What patients are told about booking, matching the rules the server enforces (AppointmentService).

export const booking =
  'Your request is sent to the surgeon as Pending. You will see it change to Confirmed once the surgeon accepts it.'

export const reschedule =
  'A new time goes back to Pending until the surgeon confirms it. Your current slot is released as soon as the change is saved.'

export const cancellation = 'Pending and confirmed appointments can be cancelled. Completed or cancelled ones cannot.'

export const BOOKING_WINDOW_DAYS = 60
