/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.ghprb.extensions.status;


import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Queue;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.PrimitiveIterator.OfLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.github.GHCommitState;
import java.util.Random;
import java.util.stream.LongStream;
/**
 *
 * @author mmitche
 */
@Extension
public class GhprbSimpleQueueStatusUpdater extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(GhprbSimpleQueueStatusUpdater.class.getName());

    public GhprbSimpleQueueStatusUpdater() {
        super("GhprbQueueStatusUpdaterTask");
    }

    @Override
    public void execute(TaskListener listener) {
        Random random = new Random();
        LongStream randomSleepTimesStream = random.longs(500,1500);
        OfLong randomSleepTimes = randomSleepTimesStream.iterator();
        
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
            // sake, so if there isn't a GHRepository, continue. This could happen on restart for instance.

            if (updateStatusAction.getRepository() == null) {
                continue;
            }

            try {
                synchronized(updateStatusAction) {
                    if (updateStatusAction.getStarted()) {
                        continue;
                    }

                    // It's GHPRB launched, do a status update.  To avoid updating
                    // the status to pending for items that were cancelled, made running, etc.
                    // while we are in this loop.....we.....I have no idea.
                    updateStatus(updateStatusAction, queuePosition, queueLength);
                    
                    try {
                        // Sleep for a random time between .5 and 1.5 seconds before attempting to 
                        // run another update to avoid API rate limit issues.
                        Thread.sleep(randomSleepTimes.next());
                    }
                    catch (InterruptedException e) {
                        // We were interrupted here....break from the loop
                        break;
                    }
                }
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
