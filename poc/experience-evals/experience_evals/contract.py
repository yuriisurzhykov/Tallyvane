"""Wire contract shared by every provider, fixture and the reducer."""

from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator

Text = Annotated[str, Field(min_length=1)]
LocalRef = Annotated[str, Field(pattern=r"^\$[A-Za-z][A-Za-z0-9_-]{0,63}$")]


class Wire(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Evidence(Wire):
    message_id: Text
    quote: Text


class Metric(Wire):
    name: Text
    value: float
    unit: Text
    baseline: float | None
    target: float | None
    approximate: bool


class Result(Wire):
    status: Literal["not_stated", "achieved", "not_achieved", "in_progress", "rejected"]
    description: Text | None
    metrics: list[Metric]
    lesson: Text | None

    @model_validator(mode="after")
    def unknown_is_empty(self):
        if self.status == "not_stated" and (self.description is not None or self.metrics or self.lesson is not None):
            raise ValueError("A not_stated result has no description, metrics or lesson")
        if self.status != "not_stated" and self.description is None:
            raise ValueError("A stated result has a source-backed description")
        return self


class Deadline(Wire):
    due_at: Text | None
    completed_at: Text | None
    status: Literal["not_stated", "unknown", "met", "missed", "in_progress"]
    reaction: Text | None

    @model_validator(mode="after")
    def unknown_is_empty(self):
        if self.status == "not_stated" and any(v is not None for v in (self.due_at, self.completed_at, self.reaction)):
            raise ValueError("A not_stated deadline has no asserted fields")
        return self


class CompanyState(Wire):
    id: Text
    name: Text


class ProjectState(Wire):
    id: Text
    company_id: Text
    name: Text
    requirements: list[Text]
    responsibility: Text | None


class TaskState(Wire):
    id: Text
    project_id: Text
    title: Text
    requirements: list[Text]
    deadline: Deadline


class ActionState(Wire):
    id: Text
    task_id: Text
    description: Text
    reason: Text | None
    technologies: list[Text]
    approaches: list[Text]
    result: Result
    retracted: bool


class Snapshot(Wire):
    revision: Annotated[int, Field(ge=0)]
    companies: dict[str, CompanyState]
    projects: dict[str, ProjectState]
    tasks: dict[str, TaskState]
    actions: dict[str, ActionState]


class Context(Wire):
    company_id: Text | None
    project_id: Text | None
    task_id: Text | None
    action_id: Text | None


class Message(Wire):
    id: Text
    text: Text


class InterviewInput(Wire):
    state: Snapshot
    context: Context
    previous_messages: list[Message]
    message: Message


class Change(Wire):
    evidence: Evidence


class CreateCompany(Change):
    operation: Literal["create_company"]
    ref: LocalRef
    name: Text


class CreateProject(Change):
    operation: Literal["create_project"]
    ref: LocalRef
    company_id: Text
    name: Text
    requirements: list[Text]
    responsibility: Text | None


class CreateTask(Change):
    operation: Literal["create_task"]
    ref: LocalRef
    project_id: Text
    title: Text
    requirements: list[Text]
    deadline: Deadline


class CreateAction(Change):
    operation: Literal["create_action"]
    ref: LocalRef
    task_id: Text
    description: Text
    reason: Text | None
    technologies: list[Text]
    approaches: list[Text]
    result: Result


class AddTechnologies(Change):
    operation: Literal["add_technologies"]
    action_id: Text
    technologies: list[Text]


class ReplaceTechnologies(Change):
    operation: Literal["replace_technologies"]
    action_id: Text
    technologies: list[Text]


class SetResult(Change):
    operation: Literal["set_result"]
    action_id: Text
    result: Result


class SetReason(Change):
    operation: Literal["set_reason"]
    action_id: Text
    reason: Text | None


class AddApproaches(Change):
    operation: Literal["add_approaches"]
    action_id: Text
    approaches: list[Text]


class SetDescription(Change):
    operation: Literal["set_description"]
    action_id: Text
    description: Text


class MoveAction(Change):
    operation: Literal["move_action"]
    action_id: Text
    task_id: Text


class RetractAction(Change):
    operation: Literal["retract_action"]
    action_id: Text


class SetDeadline(Change):
    operation: Literal["set_deadline"]
    task_id: Text
    deadline: Deadline


class SetRequirements(Change):
    operation: Literal["set_requirements"]
    target_type: Literal["project", "task"]
    target_id: Text
    requirements: list[Text]


Operation = Union[CreateCompany, CreateProject, CreateTask, CreateAction, AddTechnologies,
                  ReplaceTechnologies, SetResult, SetReason, AddApproaches, SetDescription,
                  MoveAction, RetractAction, SetDeadline, SetRequirements]


class Unresolved(Wire):
    text: Text
    candidate_ids: list[Text]
    evidence: Evidence


class Proposal(Wire):
    base_revision: Annotated[int, Field(ge=0)]
    changes: list[Operation]
    unresolved_references: list[Unresolved]


def output_schema():
    """All wire fields are required; nullable facts are explicit; no provider SDK required."""
    return Proposal.model_json_schema()
