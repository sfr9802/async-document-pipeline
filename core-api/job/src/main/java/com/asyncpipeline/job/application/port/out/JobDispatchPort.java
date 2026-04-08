package com.asyncpipeline.job.application.port.out;

import com.asyncpipeline.job.application.dto.JobDispatchRequest;

public interface JobDispatchPort {

    void dispatch(JobDispatchRequest request);
}
