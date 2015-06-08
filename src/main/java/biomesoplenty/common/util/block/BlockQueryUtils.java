/*******************************************************************************
 * Copyright 2014, the Biomes O' Plenty Team
 * 
 * This work is licensed under a Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International Public License.
 * 
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/4.0/.
 ******************************************************************************/

package biomesoplenty.common.util.block;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

public class BlockQueryUtils
{
    
    public static class BlockQueryParseException extends Exception
    {
        public BlockQueryParseException(String message)
        {
            super(message);
        }
    }
    
    
    /***** IBlockPosQuery *****/
    // for queries on a particular block position in the world
    
    public static interface IBlockPosQuery
    {
        public boolean matches(World world, BlockPos pos);
    }
    
    // match any position
    public static class BlockPosQueryAnything implements IBlockPosQuery
    {
        @Override
        public boolean matches(World world, BlockPos pos) {
            return true;
        }
    }
    
    // match no positions
    public static class BlockPosQueryNothing implements IBlockPosQuery
    {
        @Override
        public boolean matches(World world, BlockPos pos) {
            return false;
        }
    }

    // Match a block pos if any of the children match
    public static class BlockPosQueryOr implements IBlockPosQuery
    {
        private ArrayList<IBlockPosQuery> children;
        public BlockPosQueryOr(IBlockPosQuery... children)
        {
            this.children = new ArrayList<IBlockPosQuery>(Arrays.asList(children));
        }
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            for (IBlockPosQuery child : this.children)
            {
                if (child.matches(world, pos))
                {
                    return true;
                }
            }
            return false;
        }
        public void add(IBlockPosQuery child)
        {
            if (child != null) {this.children.add(child);}
        }
        public IBlockPosQuery instance()
        {
            return this.children.size() == 1 ? this.children.get(0) : this;
        }
    }
    
    // Match a block pos if all of the children match
    public static class BlockPosQueryAnd implements IBlockPosQuery
    {
        private ArrayList<IBlockPosQuery> children;
        public BlockPosQueryAnd(IBlockPosQuery... children)
        {
            this.children = new ArrayList<IBlockPosQuery>(Arrays.asList(children));
        }
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            for (IBlockPosQuery child : this.children)
            {
                if (!child.matches(world, pos))
                {
                    return false;
                }
            }
            return true;
        }
        public void add(IBlockPosQuery child)
        {
            if (child != null) {this.children.add(child);}
        }
        public IBlockPosQuery instance()
        {
            return this.children.size() == 1 ? this.children.get(0) : this;
        }
    }
    
    
    // Match a block pos if the child does not match
    public static class BlockPosQueryNot implements IBlockPosQuery
    {
        IBlockPosQuery child;
        public BlockPosQueryNot(IBlockPosQuery child)
        {
            this.child = child;
        }
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            return !this.child.matches(world, pos);
        }
    }
    
    // Match block positions adjacent to water
    public static class BlockPosQueryHasWater implements IBlockPosQuery
    {
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            return (world.getBlockState(pos.west()).getBlock().getMaterial() == Material.water || world.getBlockState(pos.east()).getBlock().getMaterial() == Material.water || world.getBlockState(pos.north()).getBlock().getMaterial() == Material.water || world.getBlockState(pos.south()).getBlock().getMaterial() == Material.water);
        }
    }
    
    // Match block positions with air above
    public static class BlockPosQueryAirAbove implements IBlockPosQuery
    {
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            return world.isAirBlock(pos.up());
        }
    }   
    
    // Match block positions in a height range
    public static class BlockPosQueryAltitude implements IBlockPosQuery
    {
        public int minHeight;
        public int maxHeight;
        
        public BlockPosQueryAltitude(int minHeight, int maxHeight)
        {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
        
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            return pos.getY() >= this.minHeight && pos.getY() <= this.maxHeight;
        }
    }   
    
    
    
    
    
    
    
    /***** IBlockQuery *****/
    // for queries which depend only on the block state, and not on it's neighbors or position in the world
    
    
    public static interface IBlockQuery extends IBlockPosQuery
    {
        public boolean matches(IBlockState state);
    }
    
    public static abstract class BlockQueryBase implements IBlockQuery
    {
        @Override
        public boolean matches(World world, BlockPos pos)
        {
            return this.matches(world.getBlockState(pos));
        }        
    }
    
    // Match a block pos if the child does not match
    public static class BlockQueryNot extends BlockQueryBase
    {
        IBlockQuery child;
        public BlockQueryNot(IBlockQuery child)
        {
            this.child = child;
        }
        @Override
        public boolean matches(IBlockState state)
        {
            return !this.child.matches(state);
        }
    }
    
    // Match against a particular block instance
    public static class BlockQueryBlock extends BlockQueryBase
    {
        private Block block;
        
        public BlockQueryBlock(Block block)
        {
            this.block = block;
        }
        
        @Override
        public boolean matches(IBlockState state)
        {
            return state.getBlock() == this.block;
        }
        
        public static IBlockQuery of(String blockName, boolean negated) throws BlockQueryParseException
        {
            Block block = Block.getBlockFromName(blockName);
            if (block == null)
            {
                throw new BlockQueryParseException("No block called "+blockName);
            } else {
                IBlockQuery bm = new BlockQueryBlock(block);
                return negated ? new BlockQueryNot(bm) : bm;
            }
        }
    }
    
    // Match against a particular block state instance
    public static class BlockQueryState extends BlockQueryBase
    {
        private IBlockState state;
        
        public BlockQueryState(IBlockState state)
        {
            this.state = state;
        }
        
        @Override
        public boolean matches(IBlockState state)
        {
            return state == this.state;
        }
    }
    
    // Match against a block class
    public static class BlockQueryClass extends BlockQueryBase
    {
        public static String[] packages = {"","biomesoplenty.common.block.","net.minecraft.block."};
        
        private Class<? extends Block> clazz;
        private boolean strict;

        public BlockQueryClass(Class<? extends Block> clazz)
        {
            this(clazz, false);
        }
        
        public BlockQueryClass(Class<? extends Block> clazz, boolean strict)
        {
            this.clazz = clazz;
            this.strict = strict;
        }
        
        @Override
        public boolean matches(IBlockState state)
        {
            return strict ? (state.getBlock().getClass() == this.clazz) : this.clazz.isInstance(state.getBlock());
        }
        
        public static IBlockQuery of(String className, boolean negated, boolean strict) throws BlockQueryParseException
        {
            Class clazz;
            for (String packageName : packages)
            {
                try {
                    clazz = Class.forName(packageName+className);
                } catch (Exception e) {
                    continue;
                }
                if (Block.class.isAssignableFrom(clazz))
                {
                    IBlockQuery bm = new BlockQueryClass(clazz, strict);
                    return negated ? new BlockQueryNot(bm) : bm;
                }
            }
            throw new BlockQueryParseException("No class found extending from Block called "+className);
        }
    }
    
    // Match against a state property value
    public static class BlockQueryProperty extends BlockQueryBase
    {
        private static Pattern propertyNameValueRegex = Pattern.compile("^\\s*((\\w+)\\s*=\\s*)?([\\w\\|]+)\\s*$");
        
        private String propName;
        private String[] propValues;
        
        public BlockQueryProperty(String propName, String... propValues)
        {
            this.propName = propName;
            this.propValues = propValues;
        }
        
        @Override
        public boolean matches(IBlockState state)
        {
            ImmutableMap properties = state.getProperties();
            for (Object property : properties.keySet())
            {
                if (((IProperty)property).getName().equalsIgnoreCase(this.propName))
                {
                    String thisPropValue = ((Comparable)properties.get(property)).toString();
                    for (String value : this.propValues)
                    {
                        if (thisPropValue.equalsIgnoreCase(value))
                        {
                            return true;
                        }
                    }
                    return false;
                }
            }
            return false;
        }
        
        public static IBlockQuery of(String nameValuePair, boolean negated) throws BlockQueryParseException
        {
            Matcher m = propertyNameValueRegex.matcher(nameValuePair);
            if (!m.find())
            {
                throw new BlockQueryParseException("Syntax error in "+nameValuePair);
            }
            String propName = m.group(2);
            String[] propValues = m.group(3).split("\\|");          
            if (propName == null) {propName = "variant";}
            IBlockQuery bm = new BlockQueryProperty(propName, propValues);
            return negated ? new BlockQueryNot(bm) : bm;
        }
    }
    
    // Match against a block material
    public static class BlockQueryMaterial extends BlockQueryBase
    {
        private Material material;
        public BlockQueryMaterial(Material material)
        {
            this.material = material;
        }
        @Override
        public boolean matches(IBlockState state)
        {
            return state.getBlock().getMaterial() == this.material;
        }
        
        public static IBlockQuery of(String materialName, boolean negated) throws BlockQueryParseException
        {
            try {
                Field f = Material.class.getField(materialName);
                Object mat = f.get(null);
                if (mat instanceof Material)
                {
                    IBlockQuery bm = new BlockQueryMaterial((Material)mat);
                    return negated ? new BlockQueryNot(bm) : bm;
                }
            } catch (Exception e) {;}
            throw new BlockQueryParseException("No block material found called "+materialName);
        }
    }
    
    

    
    
    
    /***** Parsing from a string *****/
    
    
    public static final Map<String, IBlockPosQuery> predefined = new HashMap<String, IBlockPosQuery>();
    
    // regular expression to match a token in a block query - eg  'sand' '%BlockBOPLeaves' '[facing=up]' etc
    private static Pattern nextTokenRegex = Pattern.compile("^(!?([\\w:]+|\\%\\w+|\\$\\w+|~\\w+|\\[.+\\]|@\\w+))");
    // regular expression for splitting up a comma delimited list
    private static Pattern commaDelimitRegex = Pattern.compile("\\s*,\\s*");
    
    // parse the given block query string and return the equivalent IBlockQuery object
    public static IBlockPosQuery parseQueryString(String spec) throws BlockQueryParseException
    {
        BlockPosQueryOr bmAny = new BlockPosQueryOr();
        String[] subspecs = commaDelimitRegex.split(spec);
        for (String subspec : subspecs)
        {
            bmAny.add( parseQueryStringSingle(subspec) );
        }
        return bmAny.instance();
    }
    
    
    private static IBlockPosQuery parseQueryStringSingle(String spec) throws BlockQueryParseException
    {
        BlockPosQueryAnd bmAll = new BlockPosQueryAnd();
        
        Matcher m = nextTokenRegex.matcher(spec);
        while (spec.length() > 0)
        {
            
            m = nextTokenRegex.matcher(spec);
            if (!m.find())
            {
                throw new BlockQueryParseException("Syntax error in "+spec);
            }
            String token = m.group(0);
            spec = spec.substring(token.length());
            
            boolean negated = false;
            if (token.charAt(0) == '!')
            {
                negated = true;
                token = token.substring(1);
            }
            
            if (token.charAt(0) == '%')
            {
                bmAll.add( BlockQueryClass.of(token.substring(1), negated, false) );
            }
            else if (token.charAt(0) == '$')
            {
                bmAll.add( BlockQueryClass.of(token.substring(1), negated, true) );
            }
            else if (token.charAt(0) == '~')
            {
                bmAll.add( BlockQueryMaterial.of(token.substring(1), negated) );
            }
            else if (token.charAt(0)=='[')
            {
                String[] subtokens = commaDelimitRegex.split(token.substring(1, token.length() - 1));
                for (String subtoken : subtokens)
                {
                    bmAll.add( BlockQueryProperty.of(subtoken, negated) );
                }
            }
            else if (token.charAt(0) == '@')
            {
                IBlockPosQuery bm = predefined.get(token.substring(1));
                if (bm == null)
                {
                    throw new BlockQueryParseException("No predefined query named " + token.substring(1));
                }
                bmAll.add( negated ? new BlockPosQueryNot(bm) : bm );
            }
            else
            {
                bmAll.add( BlockQueryBlock.of(token, negated) );
            }
        }
        
        return bmAll.instance();
        
    } 
    
    
}