import { defineApcaContrastSpecs } from "test-kit/specs/contrast-apca";
import { readStoryManifest } from "./story-manifest";

defineApcaContrastSpecs(readStoryManifest());
