# AI Experiments

## Experiment A — zero-shot local classification

Input only structured numeric context.

Example:

```text
opens_10m=4
opens_30m=7
reopens_under_60s=3
recent_unlock_count=1
current_session_seconds=21
scheduled_block_active=true
```

Prompt asks for one of:

`INTENTIONAL | UNCERTAIN | HABITUAL`

Do not include application content.

## Experiment B — rule vs AI agreement

Run both classifiers on a local synthetic dataset and record agreement percentage in a debug-only screen.

Do not upload user data.

## Experiment C — latency

Measure first inference and warm inference. If inference delays intervention, the AI is not suitable for the critical path.

## Experiment D — battery

Compare normal operation with AI disabled.

Success means negligible enough impact for the intended session length, otherwise keep AI optional.
