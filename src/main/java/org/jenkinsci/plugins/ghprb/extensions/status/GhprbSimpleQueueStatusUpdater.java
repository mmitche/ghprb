/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.ghprb.extensions.status;


import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    private class UpdateEntry {
        private Queue.Item item;
        private GhprbUpdateQueueStatus statusAction;
        private int position;

        public UpdateEntry(Queue.Item item, GhprbUpdateQueueStatus statusAction, int position) {
            this.item = item;
            this.statusAction = statusAction;
            this.position = position;
        }

        public Queue.Item getItem() {
            return item;
        }

        public GhprbUpdateQueueStatus getStatusAction() {
            return statusAction;
        }

        public int getPosition() {
            return position;
        }
    }
    
    @Override
    public void execute(TaskListener listener) {
        Random random = new Random();
        LongStream randomSleepTimesStream = random.longs(500,1500);
        OfLong randomSleepTimes = randomSleepTimesStream.iterator();
        
        // Get a queue snapshot
        Queue queue = Jenkins.getInstance().getQueue();
        List<Queue.Item> items = queue.getApproximateItemsQuickly();
        
        HashMap<Label, Integer> queueLengthsPerLabel = new HashMap<Label, Integer>();
        ArrayList<UpdateEntry> updateEntries = new ArrayList<UpdateEntry>();

        // Walk the queue items, finding those that are launched by the ghprb.
        // Note that this is a snapshot.  We want to do our best to avoid
        // incorrect updates, etc.
        for (Queue.Item item : items) {
            // 
            // Grab the position in the queue for the particular label.
            // The positions we are recording here are reversed, so we'll
            // switch the position vs. the maximum queueLength for each label
            // when we update the status.
            Label assignedLabel = item.getAssignedLabel();
            
            if (assignedLabel == null) {
                continue;
            }
            
            int queuePosition = queueLengthsPerLabel.getOrDefault(assignedLabel, 0);
            // Find a GhprbUpdateQueueStatus item
            GhprbUpdateQueueStatus updateStatusAction = item.getAction(GhprbUpdateQueueStatus.class);
            // Grab the position in the queue for the particular label.
            // The positions we are recording here are reversed, so we'll
            // switch the position vs. the maximum queueLength for each label
            // when we update the status.
            queueLengthsPerLabel.put(assignedLabel, queuePosition + 1);
            
            // If it's not GHPRB launched, go on to the next queue item.
            if (updateStatusAction == null) {
                continue;
            }

            // We don't keep track of the GHRepository connection on the queue item for simplicities
            // sake, so if there isn't a GHRepository, continue. This could happen on restart for instance.

            if (updateStatusAction.getRepository() == null) {
                continue;
            }
            
            updateEntries.add(new UpdateEntry(item, updateStatusAction, queuePosition + 1));
        }
        
        // Run the actual update
        for (UpdateEntry updateItem : updateEntries) {
            GhprbUpdateQueueStatus updateStatusAction = updateItem.getStatusAction();
            Queue.Item queueItem = updateItem.getItem();
            int queuePosition = updateItem.getPosition();
            
            try {
                synchronized(updateStatusAction) {
                    if (updateStatusAction.getStarted()) {
                        continue;
                    }

                    int queueLength = queueLengthsPerLabel.get(queueItem.getAssignedLabel());
                    queuePosition = queueLength - queuePosition + 1;
                    StringBuilder sb = new StringBuilder();
                    sb.append(updateStatusAction.getMessage());
                    sb.append(" (");
                    sb.append(queuePosition);
                    sb.append("/");
                    sb.append(queueLength);
                    sb.append(" on ");
                    sb.append(queueItem.getAssignedLabel().getName());
                    sb.append(")");
                    String message = sb.toString();
                    updateStatusAction.getRepository().createCommitStatus(updateStatusAction.getCommitSha(), 
                            GHCommitState.PENDING, updateStatusAction.getUrl(), message, updateStatusAction.getContext());

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
        
    }

    @Override
    public long getRecurrencePeriod() {
        // Every 5 minutes
        return 5 * 60 * 1000;
    }
}
