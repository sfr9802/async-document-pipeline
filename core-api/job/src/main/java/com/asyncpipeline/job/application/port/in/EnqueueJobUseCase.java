package com.asyncpipeline.job.application.port.in;

import com.asyncpipeline.job.application.dto.EnqueueJobCommand;
import com.asyncpipeline.job.application.dto.EnqueueJobResult;

public interface EnqueueJobUseCase {

    EnqueueJobResult enqueue(EnqueueJobCommand command);
}
