import { Router, Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { z } from 'zod';
import { ArgusDatabase } from '../db/database';
import { Group } from '../types';

const updateGroupSchema = z.object({
  title: z.string().trim().min(1).max(100).optional(),
  description: z.string().trim().max(500).optional().nullable(),
  avatarUrl: z.string().trim().max(500).optional().nullable(),
  disappearingDurationSec: z.number().int().min(0).max(30 * 24 * 3600).optional().nullable()
});

export function createGroupsRouter(db: ArgusDatabase): Router {
  const router = Router();

  /**
   * Create a new group
   */
  router.post('/create', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const { title, description, memberIds, avatarUrl, disappearingDurationSec } = req.body;

    if (!title || typeof title !== 'string') {
      res.status(400).json({ error: 'Group title is required' });
      return;
    }

    const members = Array.isArray(memberIds) ? Array.from(new Set([userId, ...memberIds])) : [userId];
    const group: Group = {
      id: uuidv4(),
      title: title.trim(),
      description: description ? description.trim() : undefined,
      avatarUrl,
      createdBy: userId,
      admins: [userId],
      members,
      createdAt: Date.now(),
      disappearingDurationSec: disappearingDurationSec ?? undefined
    };

    db.groups.set(group.id, group);
    db.save();

    res.status(201).json({ success: true, group });
  });

  /**
   * Get user's groups
   */
  router.get('/', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const userGroups: Group[] = [];

    for (const g of db.groups.values()) {
      if (g.members.includes(userId)) {
        userGroups.push(g);
      }
    }

    res.json({ groups: userGroups });
  });

  /**
   * Get group details with member profiles
   */
  router.get('/:groupId', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.members.includes(userId)) {
      res.status(403).json({ error: 'Access denied: You are not a member of this group' });
      return;
    }

    const memberProfiles = group.members.map(mId => {
      const u = db.users.get(mId);
      return u
        ? {
            id: u.id,
            username: u.username,
            displayName: u.displayName,
            avatarUrl: u.avatarUrl,
            isOnline: u.isOnline,
            lastSeen: u.lastSeen
          }
        : { id: mId, displayName: 'Former Member', isOnline: false, lastSeen: 0 };
    });

    res.json({ success: true, group, memberProfiles });
  });

  /**
   * Update group settings (Title, description, avatar, disappearing timer)
   */
  router.put('/:groupId', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;

    const parseResult = updateGroupSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.admins.includes(userId)) {
      res.status(403).json({ error: 'Only group admins can modify group settings' });
      return;
    }

    const { title, description, avatarUrl, disappearingDurationSec } = parseResult.data;
    if (title !== undefined) group.title = title;
    if (description !== undefined) group.description = description || undefined;
    if (avatarUrl !== undefined) group.avatarUrl = avatarUrl || undefined;
    if (disappearingDurationSec !== undefined) {
      group.disappearingDurationSec = disappearingDurationSec === null ? undefined : disappearingDurationSec;
    }

    db.save();
    res.json({ success: true, group });
  });

  /**
   * Add members to group (Admin only)
   */
  router.post('/:groupId/add-members', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;
    const { memberIds } = req.body;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.admins.includes(userId)) {
      res.status(403).json({ error: 'Only group admins can add members' });
      return;
    }

    if (!Array.isArray(memberIds) || memberIds.length === 0) {
      res.status(400).json({ error: 'memberIds must be a non-empty array' });
      return;
    }

    memberIds.forEach((id: string) => {
      if (typeof id === 'string' && !group.members.includes(id)) {
        group.members.push(id);
      }
    });

    db.save();
    res.json({ success: true, group });
  });

  /**
   * Remove member from group (Admin only)
   */
  router.post('/:groupId/remove-member', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;
    const { memberId } = req.body;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.admins.includes(userId)) {
      res.status(403).json({ error: 'Only group admins can remove members' });
      return;
    }

    if (!memberId || typeof memberId !== 'string') {
      res.status(400).json({ error: 'Valid memberId is required' });
      return;
    }

    group.members = group.members.filter(id => id !== memberId);
    group.admins = group.admins.filter(id => id !== memberId);

    if (group.admins.length === 0 && group.members.length > 0) {
      group.admins.push(group.members[0]);
    }

    db.save();
    res.json({ success: true, group });
  });

  /**
   * Leave a group
   */
  router.post('/:groupId/leave', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.members.includes(userId)) {
      res.status(400).json({ error: 'You are not a member of this group' });
      return;
    }

    group.members = group.members.filter(id => id !== userId);
    group.admins = group.admins.filter(id => id !== userId);

    if (group.members.length === 0) {
      db.groups.delete(groupId);
    } else if (group.admins.length === 0) {
      group.admins.push(group.members[0]);
    }

    db.save();
    res.json({ success: true, message: 'Left group successfully' });
  });

  /**
   * Disband / delete group (Creator / Admin only)
   */
  router.delete('/:groupId', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const groupId = req.params.groupId as string;

    const group = db.groups.get(groupId);
    if (!group) {
      res.status(404).json({ error: 'Group not found' });
      return;
    }

    if (!group.admins.includes(userId) && group.createdBy !== userId) {
      res.status(403).json({ error: 'Only group admins or group creator can delete this group' });
      return;
    }

    db.groups.delete(groupId);
    db.save();

    res.json({ success: true, message: 'Group deleted successfully' });
  });

  return router;
}
