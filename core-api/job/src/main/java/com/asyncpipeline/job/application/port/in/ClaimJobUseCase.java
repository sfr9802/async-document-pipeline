package com.asyncpipeline.job.application.port.in;

import com.asyncpipeline.job.application.dto.ClaimJobCommand;
import com.asyncpipeline.job.application.dto.ClaimJobResult;

public interface ClaimJobUseCase {

    ClaimJobResult claim(ClaimJobCommand command);
}
