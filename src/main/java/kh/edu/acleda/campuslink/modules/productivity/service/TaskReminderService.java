package kh.edu.acleda.campuslink.modules.productivity.service;

import java.time.LocalDateTime;

/** Emits in-app reminders for productivity tasks with a configured reminder time. */
public interface TaskReminderService {

    int sendDueReminders(LocalDateTime now);
}
