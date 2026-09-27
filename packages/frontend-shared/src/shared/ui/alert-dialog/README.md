# alert-dialog

A purpose-built blocking confirmation for actions that discard unsaved work
or cannot be undone. It is not a general content surface. Use the composed
`Popup`, `Title`, and `Description` parts with actions wrapped in `Close`.

The component owns the portal, backdrop, viewport, surface tokens, and
typography. Base UI provides focus handling, keyboard behavior, and modal
semantics. Confirmation copy and action behavior come from the caller.
