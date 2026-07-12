package com.gagastudio.finmate.quests;

class QuestNotFoundException extends RuntimeException {
	QuestNotFoundException() { super("Quest was not found for the authenticated user"); }
}
