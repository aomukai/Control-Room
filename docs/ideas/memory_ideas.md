What you can steal directly from Engram
	Here are the concrete ideas worth importing.

Earlier grounding for agents (small tweak)
	Engram shows that early factual grounding improves downstream reasoning.
	You already default to R3, which is good — but you could tighten the rule:
	If an issue has epistemicStatus >= agreed, inject R1 + R3 header even when not explicitly retrieved.
	This keeps agents oriented without cost.

Cheap “concept packets” as R2 (optional, future)
	Engram’s N-gram tables are essentially micro-concept anchors.
	You intentionally skipped R2 (correctly, for v1), but Engram suggests a future upgrade:
	R2 as non-prose tokens, e.g.:

		DEEP_CURRENT → affects oxygen recyclers
		CANON → arrival day = 47
		SERYN → amber eyes

	These are not summaries — they’re routing hints.
	You don’t need them yet, but Engram says:
	If scale becomes a problem, this is the next lever.

Engram paper link:
https://github.com/deepseek-ai/Engram/blob/main/Engram_paper.pdf