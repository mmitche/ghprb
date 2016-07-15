/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.ghprb;


import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Queue;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.ghprb.extensions.GhprbCommitStatusException;
import org.kohsuke.github.GHCommitState;

import org.jenkinsci.plugins.ghprb.extensions.status.GhprbUpdateQueueStatus;
/**
 *
 * @author mmitche
 */
@Extension
public class GhprbQueueStatusUpdater extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(GhprbQueueStatusUpdater.class.getName());

    public GhprbQueueStatusUpdater() {
        super("GhprbQueueStatusUpdaterTask");
    }

    @Override
    public void execute(TaskListener listener) {
        // Get a queue snapshot
        Queue queue = Jenkins.getInstance().getQueue();
        List<Queue.Item> items = queue.getApproximateItemsQuickly();

        // Walk the queue items, finding those that are launched by the ghprb.
        // Note that this is a snapshot.  We want to do our best to avoid
        // incorrect updates, etc.
        int queuePosition = 1;
        int queueLength = items.size();
        for (Queue.Item item : items) {
            queuePosition++;
            // Find a GhprbUpdateQueueStatus item
            GhprbUpdateQueueStatus updateStatusAction = item.getAction(GhprbUpdateQueueStatus.class);
            
            // If it's not GHPRB launched, go on to the next queue item
            if (updateStatusAction == null) {
                continue;
            }
            
            // We don't keep track of the GHRepository connection on the queue item for simplicities
            // sake, so if there isn't a GHRepository, continue;
            
            if (updateStatusAction.getRepository() == null) {
                continue;
            }

            try {
                // It's GHPRB launched, do a status update.  To avoid updating
                // the status to pending for items that were cancelled, made running, etc.
                // while we are in this loop.....we.....I have no idea.
                updateStatus(updateStatusAction, queuePosition, queueLength);
            }
            catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Couldn't update queue status.", e);
            }
        }
    }

    public void updateStatus(GhprbUpdateQueueStatus updateStatusAction, int queuePosition, int queueLength) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(updateStatusAction.getMessage());
        sb.append(" (");
        sb.append(queuePosition);
        sb.append(" of ");
        sb.append(queueLength);
        sb.append(")");
        String message = sb.toString();
        updateStatusAction.getRepository().createCommitStatus(updateStatusAction.getCommitSha(), 
                GHCommitState.PENDING, updateStatusAction.getUrl(), message, updateStatusAction.getContext());
    }

    @Override
    public long getRecurrencePeriod() {
        // Every 1 minute
        return 30 * 1000;
    }
}
