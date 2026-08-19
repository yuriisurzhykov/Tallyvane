# templates

Typst templates for résumé rendering.

```
resume/    one .typ file per layout
```

## Why Typst and not headless Chromium

Two reasons, and the first one decides it. Applicant tracking systems do not
read a PDF, they pull out its text layer and parse it with heuristics — so the
text layer is the product, and Typst produces a clean one with typography of
LaTeX quality. The second reason is that Typst is a 30 MB binary using tens of
megabytes of memory, against hundreds for a headless browser, on a machine with
two gigabytes total.

The `ResumeRenderer` interface leaves the door open: swapping in Chromium later
means writing one class, and the ATS validator would start checking it against
the same criteria immediately.

## Rules every template obeys

Single column. No tables used for layout. No text inside graphics. Section
headings from a fixed vocabulary. No headers or footers. Fonts embedded with
complete glyph coverage. Dates in an unambiguous format.

These are not stylistic preferences. A two-column "beautiful" résumé is turned
into porridge by Workday.

## Verified, not assumed

Every template is rendered and then read back with PDFBox across a set of
representative compositions. If any bullet goes missing from the extracted text
or the reading order does not match the intended section order, the build
fails. A résumé that would break an ATS cannot be produced.
