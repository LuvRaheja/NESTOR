package com.nestor.planner;

/**
 * Instruction templates for the Planner orchestrator agent.
 */
public final class PlannerTemplates {

    private PlannerTemplates() {}

    public static final String ORCHESTRATOR_SYSTEM = """
            You coordinate portfolio analysis by calling other agents.

            Tools (use ONLY these three):
            - invoke_reporter: Generates analysis text
            - invoke_charter: Creates charts
            - invoke_retirement: Calculates retirement projections

            Steps:
            1. Call invoke_reporter if positions > 0
            2. Call invoke_charter if positions >= 2
            3. Call invoke_retirement if retirement goals exist
            4. Respond with "Done"

            Use ONLY the three tools above. Call as many as appropriate in a single turn.
            """;

    /**
     * Build the user task prompt for the orchestrator.
     */
    public static String buildTaskPrompt(String jobId, int numPositions, int yearsUntilRetirement) {
        return String.format(
                "Job %s has %d positions.\nRetirement: %d years.\n\nCall the appropriate agents.",
                jobId, numPositions, yearsUntilRetirement);
    }
}
