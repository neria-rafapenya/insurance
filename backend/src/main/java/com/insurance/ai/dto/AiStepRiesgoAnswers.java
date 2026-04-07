package com.insurance.ai.dto;

import java.util.List;

public record AiStepRiesgoAnswers(
    String autoVehicle,
    Integer autoAge,
    String autoUsage,
    String autoSpecs,
    String autoMileageParking,
    String homeOwnership,
    String homeUsage,
    String homeTypeDetails,
    String homeLocationContent,
    Integer healthAge,
    String healthSmoker,
    String healthPathologies,
    String healthPlan,
    String healthFamilyDetails,
    List<AiHealthMember> healthFamilyMembers,
    String travelScope,
    String travelDestination,
    Integer travelPeopleCount,
    String travelPeopleAges,
    Integer travelDurationDays,
    String travelPurpose
) {}
