package com.lessonweb.lesson.service;

import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.model.job.ErrorDetail;
import com.lessonweb.lesson.model.job.JobStatus;
import com.lessonweb.lesson.model.job.ParseJob;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ParseJobService {

    private final ConcurrentMap<String, ParseJob> jobs = new ConcurrentHashMap<>();

    public ParseJob create(String jobId, String sourceFilename) {
        ParseJob job = new ParseJob(jobId, JobStatus.PENDING, sourceFilename, Instant.now(), null, null);
        jobs.put(jobId, job);
        return job;
    }

    public ParseJob get(String jobId) {
        ParseJob job = jobs.get(jobId);
        if (job == null) {
            throw new AppException("JOB_NOT_FOUND", "解析任务不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }

    public ParseJob processing(String jobId) {
        return replace(jobId, JobStatus.PROCESSING, null, null);
    }

    public ParseJob success(String jobId, LessonDocument document) {
        return replace(jobId, JobStatus.COMPLETED, document, null);
    }

    public ParseJob fail(String jobId, String code, String message) {
        return replace(jobId, JobStatus.FAILED, null, new ErrorDetail(code, message));
    }

    private ParseJob replace(String jobId, JobStatus status, LessonDocument document, ErrorDetail error) {
        return jobs.compute(jobId, (key, current) -> {
            if (current == null) {
                throw new AppException("JOB_NOT_FOUND", "解析任务不存在", HttpStatus.NOT_FOUND);
            }
            return new ParseJob(current.jobId(), status, current.sourceFileName(), current.createdAt(), document, error);
        });
    }
}
