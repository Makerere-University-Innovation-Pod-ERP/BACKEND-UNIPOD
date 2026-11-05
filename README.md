# BACKEND-UNIPOD
Backend API for the Makerere Innovation Hub ERP, built to manage core department workflows

## Error responses

API errors use a JSON envelope containing `timestamp`, `status`, `error`, and `message`.
Unexpected server errors return a generic message; exception details, stack traces, and
validation internals are not included in responses. Unexpected exceptions are recorded in
the server log for diagnostics.
