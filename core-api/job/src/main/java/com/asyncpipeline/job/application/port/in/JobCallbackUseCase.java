package com.asyncpipeline.job.application.port.in;

import com.asyncpipeline.job.application.dto.HandleCallbackCommand;
import com.asyncpipeline.job.application.dto.HandleCallbackResult;

public interface JobCallbackUseCase {

    HandleCallbackResult handleCallback(HandleCallbackCommand command);
}
