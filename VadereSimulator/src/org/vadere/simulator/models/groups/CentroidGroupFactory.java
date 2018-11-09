package org.vadere.simulator.models.groups;

import org.vadere.state.scenario.Pedestrian;

import java.util.LinkedList;

public class CentroidGroupFactory extends GroupFactory {

	transient private CentroidGroupModel groupCollection;
	transient private GroupSizeDeterminator groupSizeDeterminator;

	private LinkedList<CentroidGroup> newGroups;

	public CentroidGroupFactory(CentroidGroupModel groupCollection,
								GroupSizeDeterminator groupSizeDet) {
		this.groupCollection = groupCollection;
		this.groupSizeDeterminator = groupSizeDet;
		this.newGroups = new LinkedList<>();
	}

	@Override
	public int getOpenPersons() {
		if (newGroups.peekFirst() == null) {
			throw new IllegalStateException("No empty group exists");
		}

		return newGroups.peekFirst().getOpenPersons();
	}

	private void assignToGroup(Pedestrian ped) {
		CentroidGroup currentGroup = newGroups.peekFirst();
		if (currentGroup == null) {
			throw new IllegalStateException("No empty group exists to add Pedestrian: " + ped.getId());
		}

		currentGroup.addMember(ped);
		ped.addGroupId(currentGroup.getID(), currentGroup.getSize());
		groupCollection.registerMember(ped, currentGroup);
		if (currentGroup.getOpenPersons() == 0) {
			newGroups.pollFirst(); // remove full group from list.
		}
	}

	public int createNewGroup() {
		CentroidGroup newGroup = groupCollection.getNewGroup(groupSizeDeterminator
				.nextGroupSize());
		newGroups.addLast(newGroup);
		return newGroup.getSize();
	}

	//listener methode (aufruf
	public void elementAdded(Pedestrian pedestrian) {
		assignToGroup(pedestrian);
	}

	public void elementRemoved(Pedestrian ped) {
		CentroidGroup group = groupCollection.removeMember(ped);
//		System.out.printf("Remove ped %s from group %s %n", ped.getId(), group != null ? group.getID() : "noGroup");
	}

	public GroupSizeDeterminator getGroupSizeDeterminator() {
		return groupSizeDeterminator;
	}

	public void setGroupSizeDeterminator(GroupSizeDeterminator groupSizeDeterminator) {
		this.groupSizeDeterminator = groupSizeDeterminator;
	}
}
