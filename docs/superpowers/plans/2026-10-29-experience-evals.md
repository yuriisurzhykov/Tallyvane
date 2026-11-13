# Experience Evaluation Bench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Compare API models against reproducible interview update scenarios.

**Architecture:** Independent CLI under `poc/experience-evals`; typed proposals enter an atomic state reducer. A deterministic scorer compares full gold and actual state; adapters expose provider usage and diagnostics.

**Tech Stack:** Python 3.11+, Pydantic 2, PyYAML 6, standard library HTTP and unittest.

**Spec:** `poc/experience-evals/DESIGN.md`.

## Global Constraints

- One result object per action, including explicit unknown state.
- Multiple companies/projects/tasks/actions; stable application-owned IDs.
- Synthetic fixtures only; never commit API keys or private source material.
- No live rankings derived from the offline scripted oracle.
- No modifications to backend or existing knowledge-core behavior.

## Review Focus

- Corrections must not modify another project with an identically named task.
- Invalid later operations must roll back every operation in the batch.
- Model transport failures and truncated JSON must fail visibly and retain usage where available.
- Actual model inputs must not contain gold operations or expected answers.
- Cost is unknown when usage/prices are unavailable, including failed calls.

### Task 1: Contract and reducer

Create `experience_evals/contract.py`, `state.py`, and `tests/test_state.py`. Test creation, amendments, evidence, singular result, revision checks, idempotency and transactional rollback before implementing `apply_proposal(state, proposal, message, id_factory)`.

- [x] Write and run failing reducer tests.
- [x] Implement typed operations, schema export and atomic reducer.
- [x] Run the reducer suite.

### Task 2: Dataset and evaluator

Create `scenarios/*.yaml`, `dataset.py`, `runner.py`, `tests/test_runner.py`. `load_scenarios(path)` validates gold replay. `run_suite(cases, provider, mode, repeats)` records isolated/chained results without putting expected data into inputs.

- [x] Write failing scenario/scorer tests.
- [x] Implement scenarios, exact canonical scoring, repeat and failure handling.
- [x] Verify all gold cases and mutation regressions.

### Task 3: API adapters and reports

Create `providers.py`, `report.py`, `__main__.py`, `providers.example.yaml`, README and offline CI. Use a local HTTP test server for real request/response protocol checks. Emit JSON/CSV and self-contained HTML; document commands and limits.

- [x] Write failing protocol and cost tests.
- [x] Implement four protocol adapters, CLI and report.
- [x] Run the whole standalone test suite, dataset validation and oracle demo.
- [x] Review the full change, fix findings and prepare a draft pull request.
