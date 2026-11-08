package com.navy.game;

/** Wake a rule queue after the transaction commits, not on a timer. */
public record MatchRequested(String ruleSet) {}
