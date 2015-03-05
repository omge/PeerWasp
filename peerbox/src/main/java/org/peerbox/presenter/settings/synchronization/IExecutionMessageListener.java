package org.peerbox.presenter.settings.synchronization;

import net.engio.mbassy.listener.Handler;

import org.peerbox.app.manager.file.LocalFileDesyncMessage;
import org.peerbox.app.manager.file.FileExecutionFailedMessage;
import org.peerbox.app.manager.file.LocalShareFolderMessage;
import org.peerbox.app.manager.file.RemoteFileDeletedMessage;
import org.peerbox.app.manager.file.RemoteFileMovedMessage;
import org.peerbox.app.manager.file.RemoteShareFolderMessage;
import org.peerbox.events.IMessageListener;
import org.peerbox.presenter.settings.synchronization.messages.FileExecutionStartedMessage;
import org.peerbox.presenter.settings.synchronization.messages.FileExecutionSucceededMessage;

public interface IExecutionMessageListener extends IMessageListener {

	@Handler
	void onExecutionStarts(FileExecutionStartedMessage message);
	
	@Handler
	void onExecutionSucceeds(FileExecutionSucceededMessage message);
	
	@Handler
	void onExecutionFails(FileExecutionFailedMessage message);
	
	@Handler
	void onFileSoftDeleted(LocalFileDesyncMessage message);
	
	@Handler
	void onFileRemotelyDeleted(RemoteFileDeletedMessage message);
	
	@Handler
	void onFileRemotelyMoved(RemoteFileMovedMessage message);
	
	@Handler
	void onRemoteFolderShared(RemoteShareFolderMessage message);
	
	@Handler
	void onLocalFolderShared(LocalShareFolderMessage message);
}
