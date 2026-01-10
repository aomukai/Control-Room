package com.miniide.models;

public class CreditEvent {

    private String id;
    private String agentId;
    private double amount;
    private String reason;
    private String verifiedBy;
    private long timestamp;
    private CreditContext context;
    private RelatedEntity relatedEntity;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public CreditContext getContext() {
        return context;
    }

    public void setContext(CreditContext context) {
        this.context = context;
    }

    public RelatedEntity getRelatedEntity() {
        return relatedEntity;
    }

    public void setRelatedEntity(RelatedEntity relatedEntity) {
        this.relatedEntity = relatedEntity;
    }

    public static class CreditContext {
        private String trigger;
        private String triggeredBy;
        private String triggerRef;
        private String outcome;
        private String outcomeRef;

        public String getTrigger() {
            return trigger;
        }

        public void setTrigger(String trigger) {
            this.trigger = trigger;
        }

        public String getTriggeredBy() {
            return triggeredBy;
        }

        public void setTriggeredBy(String triggeredBy) {
            this.triggeredBy = triggeredBy;
        }

        public String getTriggerRef() {
            return triggerRef;
        }

        public void setTriggerRef(String triggerRef) {
            this.triggerRef = triggerRef;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public String getOutcomeRef() {
            return outcomeRef;
        }

        public void setOutcomeRef(String outcomeRef) {
            this.outcomeRef = outcomeRef;
        }
    }

    public static class RelatedEntity {
        private String type;
        private String id;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
