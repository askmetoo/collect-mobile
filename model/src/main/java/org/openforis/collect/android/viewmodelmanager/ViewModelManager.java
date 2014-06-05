package org.openforis.collect.android.viewmodelmanager;

import org.openforis.collect.android.gui.util.meter.Timer;
import org.openforis.collect.android.viewmodel.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Daniel Wiell
 */
public class ViewModelManager {
    private final ViewModelRepository repo;
    private UiSurvey selectedSurvey;
    private UiRecord selectedRecord;
    private UiNode selectedNode;

    public ViewModelManager(ViewModelRepository repo) {
        this.repo = repo;
    }

    public UiSurvey getSelectedSurvey() {
        return selectedSurvey;
    }

    public Integer getSelectedRecordId() {
        return selectedRecord == null ? null : selectedRecord.getId();
    }

    public boolean isRecordSelected(int recordId) {
        return selectedNode != null && selectedRecord.getId() == recordId;
    }

    public void selectSurvey(UiSurvey survey) {
        selectedSurvey = survey;
        addRecordPlaceholders(survey);
        selectNode(survey.getFirstChild().getId());
    }

    public void addRecord(UiRecord record) {
        validateRequiredness(record);
        record.updateStatusOfNodeAndDescendants(); // TODO: This should sbe done at record.init()? Ugly anyway
        record.updateStatusOfParents();
        selectedSurvey.addRecord(record);
        repo.insertRecord(record);
        selectedRecord = record;
    }

    public UiRecord selectRecord(int recordId) {
        this.selectedRecord = loadRecord(recordId);
        return loadRecord(recordId);
    }

    private UiRecord loadRecord(int recordId) {
        UiRecord record = repo.recordById(selectedSurvey, recordId);
        if (record == null)
            throw new IllegalStateException("No record found with id " + recordId);
        record.updateStatusOfNodeAndDescendants();
        record.updateStatusOfParents(); // TODO: This should sbe done at record.init()? Ugly anyway
        return record;
    }

    public void addEntity(final UiEntity entity) {
        validateRequiredness(entity);
        entity.updateStatusOfNodeAndDescendants(); // TODO: This should sbe done at record.init()? Ugly anyway
        entity.updateStatusOfParents();
        Timer.time(ViewModelRepository.class, "insertEntity", new Runnable() {
            public void run() {
                repo.insertEntity(entity);
            }
        });
    }

    private void validateRequiredness(UiInternalNode node) {
        for (UiNode child : node.getChildren()) {
            if (child instanceof UiInternalNode)
                validateRequiredness((UiInternalNode) child);
            if (child instanceof UiAttribute) {
                validateRequiredness((UiAttribute) child);
            }
        }
    }

    private void validateRequiredness(UiAttribute attribute) {
        if (attribute.getDefinition().required && attribute.isEmpty())
            attribute.setStatus(UiNode.Status.VALIDATION_ERROR);
    }

    public UiNode lookupNode(int nodeId) {
        UiNode node = selectedSurvey.lookupRecordCollection(nodeId);
        if (node == null)
            node = selectedRecord.lookupNode(nodeId);
        if (node == null)
            throw new IllegalStateException("Node not found: " + nodeId);
        return node;
    }

    public UiAttributeCollection selectedAttributeCollection() {
        if (!(selectedNode instanceof UiAttributeCollection))
            throw new IllegalStateException("Selected node is not an attribute collection: " + selectedNode);
        return (UiAttributeCollection) selectedNode;
    }

    public UiEntityCollection selectedEntityCollection() {
        if (!(selectedNode instanceof UiEntityCollection))
            throw new IllegalStateException("Selected node is not an entity collection: " + selectedNode);
        return (UiEntityCollection) selectedNode;
    }

    public UiNode selectedNode() {
        return selectedNode;
    }

    public UiNode selectNode(int nodeId) {
        selectedNode = lookupNode(nodeId);
        return selectedNode;
    }

    public void addAttribute(UiAttribute attribute) {
        repo.insertAttribute(attribute);
    }

    public void updateAttribute(UiAttribute attribute, Map<UiAttribute, UiAttributeChange> attributeChanges) {
        // TODO: Update status and relevance of all related attributes, if it changed
        // TODO: We want attribute change events, not just validation errors
        UiNode.Status oldRecordStatus = attribute.getUiRecord().getStatus();
        List<Map<String, Object>> statusChanges = new ArrayList<Map<String, Object>>();
        for (final UiAttribute changedAttribute : attributeChanges.keySet()) {
            UiAttributeChange attributeChange = attributeChanges.get(changedAttribute);
            changedAttribute.setValidationErrors(attributeChange.validationErrors);
            if (attributeChange.relevanceChange || attributeChange.statusChange) {
                changedAttribute.updateStatus(attributeChange.validationErrors);
                if (attributeChange.relevanceChange)
                    changedAttribute.setRelevant(!changedAttribute.isRelevant());
                statusChanges.add(new HashMap<String, Object>() {{
                    put("id", changedAttribute.getId());
                    put("status", changedAttribute.getStatus().name());
                    put("relevant", changedAttribute.isRelevant());
                }});
            }
        }
        UiNode.Status newRecordStatus = attribute.getUiRecord().getStatus();
        if (oldRecordStatus == newRecordStatus)
            repo.updateAttribute(attribute, statusChanges);
        else
            repo.updateAttribute(attribute, statusChanges, newRecordStatus);
    }

    private void addRecordPlaceholders(UiSurvey uiSurvey) {
        List<UiRecord.Placeholder> recordPlaceholders = repo.surveyRecords(uiSurvey.getId());
        for (UiRecord.Placeholder record : recordPlaceholders)
            uiSurvey.lookupRecordCollection(record.getRecordCollectionName()).addChild(record);
    }

    public void removeNode(UiNode node) {
        node.removeFromParent();
        repo.removeNode(node);
    }
}