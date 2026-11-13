# Model call specs

Every call to the model is a named, versioned spec: what goes in, the JSON that must come out, an example of
each, and what the backend checks before anything is kept. A spec's name and version are part of the memo
key, so changing a prompt means a new version.

| Spec | Status | Endpoint | What it does |
|---|---|---|---|
| [`extract_facts@1`](extract_facts.json) | implemented | `POST /words` | Facts from what the person said, each quoting their words |
| [`correct_fact@1`](correct_fact.json) | implemented | `POST /corrections` | One fact replaced by the person's correction |
| `write_bullets@1` | planned | `GET /resume` | Résumé wording. The MVP builds the résumé by selection instead, 0 tokens |
| `parse_vacancy@1` | planned | vacancies | Requirements of a vacancy the person pasted, matched to their skills |
| `route_intent@1` | planned | `POST /words` | Which channel a free utterance belongs to (story, debrief, offer…) |
| `extract_debrief@1` | planned | interviews | What happened at an interview |
| `grade_practice@1` | planned | practice | Feedback on a practice answer |
| `extract_reflection@1` | planned | reflections | What the person learned or wants to change |
| `import_resume@1` | planned | onboarding | Stories and facts from an existing résumé |
| `parse_offer@1` | planned | offers | Terms of a job offer |
| `draft_role@1` | planned | route | A role track for a goal the catalog lacks |

The planned specs have their full contracts in the design (the "Model call specs" plan); each lands here,
in this format, with the endpoint that calls it.
