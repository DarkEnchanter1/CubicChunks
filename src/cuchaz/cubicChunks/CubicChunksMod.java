/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.cubicChunks;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;

import cuchaz.magicMojoModLoader.api.Mod;
import cuchaz.magicMojoModLoader.api.ModMetadata;
import cuchaz.magicMojoModLoader.api.Version;
import cuchaz.magicMojoModLoader.api.events.EncodeChunkEvent;
import cuchaz.magicMojoModLoader.api.events.InitChunkProviderClientEvent;
import cuchaz.magicMojoModLoader.api.events.InitChunkProviderServerEvent;

public class CubicChunksMod implements Mod
{
	// define one instance of the metadata
	private static final ModMetadata m_meta;
	static
	{
		m_meta = new ModMetadata();
		m_meta.setId( "cubic-chunks" );
		m_meta.setVersion( new Version( "0.1 beta" ) );
		m_meta.setName( "Cubic Chunks" );
	}
	
	@Override
	public ModMetadata getMetadata( )
	{
		return m_meta;
	}
	
	public void handleEvent( InitChunkProviderServerEvent event )
	{
		event.setCustomChunkProvider( new CubicChunkProviderServer( event.getWorld() ) );
	}
	
	public void handleEvent( InitChunkProviderClientEvent event )
	{
		event.setCustomChunkProvider( new CubicChunkProviderClient( event.getWorld() ) );
	}
	
	public void handleEvent( EncodeChunkEvent event )
	{
		// check for our chunk instance
		if( event.getChunk() instanceof Column )
		{
			Column column = (Column)event.getChunk();
			
			// encode the column
			try
			{
				byte[] data = column.encode( event.isFirstTime(), event.getFlagsYAreasToUpdate() );
				event.setData( data );
			}
			catch( IOException ex )
			{
				LogManager.getLogger().error( String.format( "Unable to encode data for column (%d,%d)", column.xPosition, column.zPosition ), ex );
			}
		}
	}
}